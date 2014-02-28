import oracle.apps.scm.pricing.priceExecution.pricingProcesses.engine.PricingUtil

//parameter priceElementCode, performRounding, priceElementUsageCode
def roundingRuleAssignmentTypeCode = 'CURRENCY'
finer '* Begin Create Charge Component with RunningUnitPrice for '+priceElementCode
sdo.Charge.findAll { ch -> !global.invalid?.lines?.contains(ch.ParentEntityId) }.each { charge ->
    // rounding
    if ( performRounding ) {
        finer '* Rounding '+global.charge[charge.ChargeId].runningUnitPrice
        def roundMap = PricingUtil.round(global.charge[charge.ChargeId].runningUnitPrice, roundingRuleAssignmentTypeCode, charge.CurrencyCode)
        if (0.0 != roundMap?.differential) {
            // create the charge component for the rounding differential
            //finer '* Creating rounding adj for '+roundMap?.differential
            def roundComp = sdo.createDataObject('ChargeComponent')
            roundComp.ChargeComponentId = global.chargeComponentIdCounter++
            roundComp.ChargeId = charge.ChargeId
            roundComp.SequenceNumber = global.charge[charge.ChargeId].compSeqCounter++
            roundComp.CurrencyCode = charge.CurrencyCode
            roundComp.SourceTypeCode = 'ROUNDING_ADJUSTMENT'
            roundComp.SourceId = roundMap.ruleId

            global.charge[charge.ChargeId].runningUnitPrice = roundMap.roundingOutput

            roundComp.PriceElementCode = global.pricingObjectParameter.ROUNDING_ADJ.priceElementCode
            roundComp.Explanation = PricingUtil.getPricingMessage(global.pricingObjectParameter.ROUNDING_ADJ.explanationMessageName, roundComp.SourceId)

            def unitPrice = roundComp.createDataObject('UnitPrice')
            unitPrice.Value = roundMap.differential
            unitPrice.CurrencyCode = charge.CurrencyCode
            finer '* Stamping rounding unit price ' +unitPrice.Value + ' ' + unitPrice.CurrencyCode
    
            
            if (charge.PricedQuantity) {                
                def extAmt = roundComp.createDataObject('ExtendedAmount')
                extAmt.Value = unitPrice.value * charge.PricedQuantity.Value
                extAmt.CurrencyCode = unitPrice.CurrencyCode
            }
        }
    }

    def cc = sdo.createDataObject('ChargeComponent')
    //finer '* Created Charge Component '
    cc.ChargeComponentId = global.chargeComponentIdCounter++
    cc.ChargeId = charge.ChargeId
    cc.PriceElementCode = priceElementCode
    cc.SequenceNumber = global.charge[charge.ChargeId].compSeqCounter++
    cc.CurrencyCode = charge.CurrencyCode
    if (priceElementUsageCode)
        cc.PriceElementUsageCode = priceElementUsageCode

    def unitPrice = cc.createDataObject('UnitPrice')
    unitPrice.Value = global.charge[charge.ChargeId].runningUnitPrice
    unitPrice.CurrencyCode = charge.CurrencyCode
    finer '* Stamping unit price ' +unitPrice.Value + ' ' + unitPrice.CurrencyCode

    if (charge.PricedQuantity) {
        def extAmt = cc.createDataObject('ExtendedAmount')
        extAmt.Value = unitPrice.value * charge.PricedQuantity.Value
        extAmt.CurrencyCode = unitPrice.CurrencyCode
    }
}
