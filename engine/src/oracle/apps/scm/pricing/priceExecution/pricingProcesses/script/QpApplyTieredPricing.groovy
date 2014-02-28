import oracle.apps.scm.pricing.priceExecution.pricingProcesses.engine.PricingUtil;

/**
 * Evaluates and applies all tiers in the queue for a given phase (e.g., segment
 * price, ceiling price, automatic discount, etc.). Tiers are applied in bulk
 * after they are added by a parent step to the queue, and the queues are
 * striped by a particular phase because tiers can be applied at multiple points
 * in a pricing process.
 * <p>
 * NOTE: phase is not the same as pricing object. The pricing object defines
 * that the tier comes from a price list or discount, but the phase further and
 * uniquely identifies what kind of price list. There is generally a 1-1
 * relationship between a tier's phase and its pricing object for tiers attached
 * to discounts; however, there is a M-1 relationship for tiers attached to
 * price lists because there are multiple types of price lists.
 * 
 * @param phaseKey the phase code used to filter a particular tier queue
 * @param pricingObjectKey the pricing object type used to retrieve the price element code and explanation message for the tier adjustments
 */

def tierQueue = global.tierQueue[phaseKey]
if ( tierQueue==null ) return
finer 'There are '+tierQueue.size()+' items in the tier queue'
tierQueue.each { tq ->
    def tierHeaderId = tq.tierHeaderId
    def chargeId = tq.chargeId
    finer 'Processing tier header '+tierHeaderId+' on charge '+chargeId

    def charge = sdo.Charge.find { it.ChargeId==chargeId && 'LINE'==it.ParentEntityCode } 
    assert charge!=null
    if ( global.invalid.lines.contains(charge.ParentEntityId) ) return

    def line = sdo.get("Line[LineId=${charge.ParentEntityId}]")
    assert line!=null

    // get tier header
    def tierHeader = queryVO(PRICING_PROCESS_AM_INST, PRICING_PROCESS_AM, 'TierHeader1', 'TierHeaderLookup', [tpId:tq.tierHeaderId])?.first()
    if ( tierHeader==null ) {
        // throw error
        def msg = sdo.createDataObject("PricingMessage");
        msg.PricingMessageId = getNextId()
        // TODO: parent entity could be line or pricing term
        msg.ParentEntityCode = 'LINE'
        msg.ParentEntityId = line.LineId
        msg.MessageTypeCode = 'ERROR'
        msg.MessageName = 'QP_PDP_TP_NOT_FOUND'
        msg.MessageText = getFndMessage('QP', msg.MessageName, [:])
        return
    }

    // calculate tier basis value
    def tierBasisVal = 0.0 // Groovy instantiates as BigDecimal by default
    switch (tierHeader.TierBasisTypeCode) {
    case 'ITEM_QUANTITY':
        switch (tierHeader.AggregationMethodCode) {
        case 'ON_LINE':
            tierBasisVal = line.LineQuantity.Value
            break
        case 'ON_DOCUMENT':
            tierQueue.findAll {
                it.tierHeaderId==tq.tierHeaderId && \
                sdo.Charge.find { it.ChargeId==tq.chargeId && 'LINE'==it.ParentEntityCode && !global.invalid.lines.contains(it.ParentEntityId) }!=null
            }
            .each {
                def line = sdo.get("Line[LineId=${tq.charge.ParentEntityId}]")
                assert line!=null
                tierBasisVal += line.LineQuantity.Value
            }
            break
        }
        break
    case 'USAGE_QUANTITY':
        // TODO
        break
    case 'AMOUNT':
        // TODO
        break
    }
    finer '\t'+tierHeader.AggregationMethodCode+', '+tierHeader.TierBasisTypeCode+', '+tierHeader.GraduatedCode+' tier basis = '+tierBasisVal

    // get tier lines
    // TODO: check how sort() treats null values, or assume MinimumValue!=null
    queryVO(PRICING_PROCESS_AM_INST, PRICING_PROCESS_AM, 'TierLine1', 'TierLineLookup', [tpId:tq.tierHeaderId])
    .findAll {
        'HIGHEST_TIER'==tierHeader.GraduatedCode && !(tierBasisVal>=it.MinimumValue && tierBasisVal<it.MaximumValue) \
        || \
        'ALL_APPLICABLE_TIERS'==tierHeader.GraduatedCode && tierBasisVal>=it.MinimumValue
    }
    .sort { it.MinimumValue }
    .each { tierLine ->
        def satisfied = 0.0
        switch (tierHeader.GraduatedCode) {
        case 'HIGHEST_TIER':
            satisfied = tierBasisVal
            break
        case 'ALL_APPLICABLE_TIERS':
            satisfied = (tierBasisVal-tierLine.MinimumValue).min(tierLine.MaximumValue-tierLine.MinimumValue)
            break
        }
        finer '\tProcessing tier line '+tierLine.MinimumValue+'-'+tierLine.MaximumValue+' ('+ \
            tierLine.AdjustmentAmount+' '+tierLine.AdjustmentTypeCode+(tierLine.PriceElementCode ? ' of '+tierLine.PriceElementCode : '')+') '+ \
            'with satisfied value '+satisfied

        // retrieve adjustment basis value, if necessary
        def adjBasisVal = 0.0
        if ( ('DISCOUNT_PERCENT'==tierLine.AdjustmentTypeCode || 'MARKUP_PERCENT'==tierLine.AdjustmentTypeCode) && tierLine.PriceElementCode!=null ) {
            def chComp = sdo.ChargeComponent.find { it.ChargeId==chargeId && it.PriceElementCode==tierLine.PriceElementCode }

            // charge component with specified price element not found
            if ( chComp==null ) {
                // throw error
                def msg = sdo.createDataObject("PricingMessage");
                msg.PricingMessageId = getNextId()
                msg.ParentEntityCode = 'LINE'
                msg.ParentEntityId = line.LineId
                msg.MessageTypeCode = 'ERROR'
                msg.MessageName = 'QP_PDP_TP_ADJ_BASIS_NT_FND_PL'
                def sessionLang = getDefaultLanguage()
                def priceListName = queryVO(PRICING_PROCESS_AM_INST, PRICING_PROCESS_AM, 'PriceListTranslation1', 'PriceListLookup', [priceListId:line.AppliedPriceListId, lang:sessionLang])[0]?.Name
                def chargeDefName = queryVO(PRICING_PROCESS_AM_INST, PRICING_PROCESS_AM, 'ChargeDefinitionTranslation1', 'ChargeDefinitionLookup', [chargeDefinitionId:charge.ChargeDefinitionId, lang:sessionLang])[0]?.Name
                def elementName = queryVO(PRICING_PROCESS_AM_INST, PRICING_PROCESS_AM, 'PricingBasisTranslation1', 'PricingBasisLookup', [pricingBasisId:tierLine.AdjustmentBasisId, lang:sessionLang])[0]?.Name
                msg.MessageText = getFndMessage('QP', msg.MessageName, [PRICE_LIST_NAME:priceListName, CHRG_DEF_NAME:chargeDefName, ELEMENT_NAME:elementName])
                return
            }
            else adjBasisVal = chComp.UnitPrice.Value
        }

        // compute unit adjustment
        def tierAdjVal = PricingUtil.computeUnitAdjustment(tierLine.AdjustmentTypeCode, global.charge[charge.ChargeId].runningUnitPrice, tierLine.AdjustmentAmount, adjBasisVal)
        finer '\t\tadjustment value = '+tierAdjVal

        // calculate weighted average across all tier rules
        assert tierBasisVal!=0
        tierAdjVal *= (satisfied/tierBasisVal)
        finer '\t\tadjustment value (weighted across all tier lines) = '+tierAdjVal

        // write tier adjustment charge component
        def ta = sdo.createDataObject('ChargeComponent')
        ta.ChargeComponentId = global.chargeComponentIdCounter++
        if ( !global.chargeComponent.containsKey(ta.ChargeComponentId) ) {
            global.chargeComponent[ta.ChargeComponentId] = [:]
        }
        ta.ChargeId = chargeId
        ta.createDataObject('UnitPrice')
        ta.UnitPrice.Value = tierAdjVal
        ta.UnitPrice.CurrencyCode = charge.CurrencyCode
        ta.CurrencyCode = charge.CurrencyCode
        if ( charge.PricedQuantity!=null ) {
            ta.createDataObject('ExtendedAmount')
            ta.ExtendedAmount.Value = ta.UnitPrice.Value * charge.PricedQuantity.Value
            ta.ExtendedAmount.CurrencyCode = ta.UnitPrice.CurrencyCode
        }
        ta.SequenceNumber = global.charge[charge.ChargeId].compSeqCounter++
        ta.PriceElementCode = global.pricingObjectParameter[pricingObjectKey].priceElementCode
        ta.Explanation = PricingUtil.getPricingMessage(global.pricingObjectParameter[pricingObjectKey].explanationMessageName, tierLine.TieredPricingLineId)
        ta.SourceId = tierLine.TieredPricingLineId
        ta.SourceTypeCode = 'TIERED_LINES'

        // adjust running unit price, etc.
        global.charge[charge.ChargeId].runningUnitPrice += ta.UnitPrice.Value
        global.chargeComponent[ta.ChargeComponentId].priceValidFrom = global.line[line.LineId].pricingDate
        global.chargeComponent[ta.ChargeComponentId].priceValidUntil = \
            queryVO(PRICING_PROCESS_AM_INST, PRICING_PROCESS_AM, 'PriceListCharge1', 'ChargeLookup', [priceListChargeId:tierHeader.ParentEntityId])[0]?.EndDate
        
    }
}
tierQueue.clear()
