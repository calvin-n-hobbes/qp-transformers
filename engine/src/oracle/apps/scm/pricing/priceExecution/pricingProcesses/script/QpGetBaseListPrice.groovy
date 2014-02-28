import oracle.apps.scm.pricing.priceExecution.pricingProcesses.engine.PricingUtil;

def serviceParam = sdo.PricingServiceParameter[0]

// set up tier queue for phase 'SEGMENT_PRICE'
if ( global.tierQueue['SEGMENT_PRICE']==null ) {
    global.tierQueue['SEGMENT_PRICE'] = []
}
    
sdo.Header.findAll { !global.invalid.headers.contains(it.HeaderId) }.each { header ->
    // process each valid transaction line
    sdo.Line.findAll { !global.invalid.lines.contains(it.LineId) }.each { line ->
        finer 'Processing line '+line.LineId
        // two ways to get header (if line were not inside the closure)
        // 1. XPath with GString
        //def header = sdo.get("Header[HeaderId=$line.HeaderId]")
        // 2. Groovy closure
        //def header = sdo.Header.find { header -> header.HeaderId==line.HeaderId }

        def binding = [
            businessUnitId: header.SellingBusinessUnitId,
            checkService: 'N',
            itemId: line.InventoryItemId,
            lineTypeCode: line.LineTypeCode,
            uomCode: line.LineQuantityUOMCode,
            orgId: line.InventoryOrganizationId,
            priceListId: line.AppliedPriceListId
        ]
    
        // get matching charges from database and filter effective dates
        def matchingCharges = queryVO(PRICING_PROCESS_AM_INST, PRICING_PROCESS_AM, 'SearchPriceListCharge1', null, binding).
            findAll { isInDateRange(it.StartDate, it.EndDate, global.line[line.LineId].pricingDate) }
    
        // if no charges found, throw error
        if ( matchingCharges.size()==0 ) {
            finer '\tNo charges found!'
            global.invalid.lines.add(line.LineId)
    
            def msg = sdo.createDataObject("PricingMessage");
            msg.PricingMessageId = getNextId()
            msg.ParentEntityCode = 'LINE'
            msg.ParentEntityId = line.LineId
            msg.MessageTypeCode = 'ERROR'
            msg.MessageName = 'QP_PDP_BASE_PRICE_NOT_FOUND'
            def itemKey = [line.InventoryItemId, line.InventoryOrganizationId]
            def priceListName = queryVO(PRICING_PROCESS_AM_INST, PRICING_PROCESS_AM, 'PriceListTranslation1', 'PriceListLookup', [priceListId:line.AppliedPriceListId, lang:getDefaultLanguage()])[0]?.Name
            msg.MessageText = getFndMessage('QP', msg.MessageName, [ITEM_NAME:global.itemAttribute[itemKey]?.itemNumber, PRICE_LIST_NAME:priceListName])
            return
        }
    
        // if necessary, sort the list of charges by item level precedence (and denormalized distance for product groups)
        if ( matchingCharges.size()>1 ) {
            matchingCharges.sort { a, b ->
                // compare numerical item level precedences
                def comparison = (getPrecedence(a.ItemLevelCode) <=> getPrecedence(b.ItemLevelCode))
                return comparison
    /* TODO: no more product group
                if ( comparison ) { // value -1 or 1, but not 0
                    return comparison
                }
                else {
                    // compare product group denormalized distances
                    if ( 'PRODUCT_GROUP'==a.ItemLevelCode ) {
                        def denorm = (a.DenormDistanceNum <=> b.DenormDistanceNum)
                        // if two product group distances are equal, then order by product group ID
                        return ( denorm ? denorm : (a.ItemId <=> b.ItemId))
                    }
                    else {
                        return comparison
                    }
                }
    */
            }
        }
        
        // group charges by charge definition
        def chargesMap = matchingCharges.groupBy { it.ChargeDefinitionId }
        finer 'There are '+matchingCharges.size()+' row(s) with '+chargesMap.size()+' unique charge definition(s)'
    
        // write the pruned entries as SDO charges and charge components
        chargesMap.values().each { row ->
            finer '\tCreating charge '+global.chargeIdCounter+1
            def charge = sdo.createDataObject('Charge')
            charge.ChargeId = global.chargeIdCounter++
            if ( !global.charge.containsKey(charge.ChargeId) ) {
                global.charge[charge.ChargeId] = [:]
            }
            def globalCharge = global.charge[charge.ChargeId]
            globalCharge.compSeqCounter = 1000L
            charge.ParentEntityId = line.LineId
            charge.ParentEntityCode = 'LINE'
            charge.ChargeDefinitionId = row[0].ChargeDefinitionId
            charge.PriceTypeCode = row[0].PriceTypeCode
            charge.ChargeTypeCode = row[0].ChargeTypeCode
            charge.ChargeSubtypeCode = row[0].ChargeSubtypeCode
            charge.CurrencyCode = global.line[line.LineId].appliedCurrencyCode
            globalCharge.runningUnitPrice = row[0].BasePrice
            def qty = line.LineQuantity.Value
            def pricedQuantity = charge.createDataObject('PricedQuantity')
            pricedQuantity.Value = qty
            pricedQuantity.UnitCode = line.LineQuantityUOMCode
            globalCharge.needsMargin = ('Y'==row[0].CalculateMarginFlag)

            // tiered pricing
            if ( row[0].TieredPricingHeaderId!=null ) {
                global.tierQueue['SEGMENT_PRICE'] << [tierHeaderId:row[0].TieredPricingHeaderId, chargeId:charge.ChargeId]
            }

            finer '\t\tCreating charge component '+(global.chargeComponentIdCounter+1)+' with unit price '+row[0].BasePrice
            def blp = sdo.createDataObject('ChargeComponent')
            blp.ChargeComponentId = global.chargeComponentIdCounter++
            if ( !global.chargeComponent.containsKey(blp.ChargeComponentId) ) {
                global.chargeComponent[blp.ChargeComponentId] = [:]
            }
            blp.ChargeId = charge.ChargeId
            blp.SequenceNumber = global.charge[charge.ChargeId].compSeqCounter++
            blp.CurrencyCode = charge.CurrencyCode
            blp.PriceElementCode = priceElemCode
            blp.SourceTypeCode = 'PRICE_LIST_CHARGE'
            blp.SourceId = row[0].PriceListChargeId            
            blp.Explanation = PricingUtil.getPricingMessage(global.pricingObjectParameter.PRICE_LIST_CHARGE.explanationMessageName, blp.SourceId, line.InventoryOrganizationId)            
            def unitPrice = blp.createDataObject('UnitPrice')
            unitPrice.Value = row[0].BasePrice
            unitPrice.CurrencyCode = charge.CurrencyCode
            def extAmt = blp.createDataObject('ExtendedAmount')
            extAmt.Value = charge.PricedQuantity.Value * unitPrice.Value
            extAmt.CurrencyCode = 'USD'
            global.chargeComponent[blp.ChargeComponentId].priceValidFrom = row[0].StartDate
            global.chargeComponent[blp.ChargeComponentId].priceValidUntil = row[0].EndDate?:new java.sql.Timestamp(Calendar.instance.time.time)
        }
    }
}

// function to return numerical precedence value for item level code
def getPrecedence(levelCode) {
    switch (levelCode) {
    case 'ITEM':
        1
        break
    // TODO: no more product group
    case 'PRODUCT_GROUP':
        2
        break
    case 'ALL_ITEMS':
        4
        break
    default:
        10
        break
    }
}
