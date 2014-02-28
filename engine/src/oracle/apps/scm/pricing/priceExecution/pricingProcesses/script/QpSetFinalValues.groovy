//parameters - comparisonElementCode
finer 'Begin Set Final Values '

finer 'Remove Charges and Charge Components for Lines and Headers in Error '
//remove errored charge components and charges
global.invalid.lines.each { lineId ->
    sdo.Charge.findAll { 'LINE'==it.ParentEntityCode && it.ParentEntityId==lineId }.each { charge ->
        sdo.ChargeComponent.findAll { it.ChargeId==charge.ChargeId }.each { comp ->
            finer 'Deleting charge component '+comp.ChargeComponentId
            comp.delete()
        }
        finer 'Deleting charge '+charge.ChargeId
        charge.delete()
    }
}

//set PercentOfComparisonElement and valid pricing dates
finer 'Set QpSetPercentOfComparisonElement for comparison element - '+comparisonElementCode
sdo.ChargeComponent.each { cmp ->
    finer 'Set QpSetPercentOfComparisonElement for ChargeComp id - '+cmp.ChargeId
    def comparisonComp = sdo.ChargeComponent.find { c -> c.ChargeId==cmp.ChargeId && comparisonElementCode==c.PriceElementCode }
    if ( 0!=comparisonComp?.UnitPrice?.Value ) {
      finer 'Set QpSetPercentOfComparisonElement comparison element unit price - '+comparisonComp.UnitPrice.Value+' for charge comp unit price '+cmp.UnitPrice.Value
      cmp.PercentOfComparisonElement = cmp.UnitPrice.Value/comparisonComp.UnitPrice.Value
    }

    def validFrom = global.chargeComponent[cmp.ChargeComponentId]?.priceValidFrom
    def validUntil = global.chargeComponent[cmp.ChargeComponentId]?.priceValidUntil
    finer 'QpSetPricingValidDates ChargeComponent ValidFrom = '+validFrom+' ValidUntil = '+validUntil
    ch = sdo.get("Charge[ChargeId=$cmp.ChargeId]")
    finer 'Found Charge - ChargeId '+ ch.ChargeId+' LineId '+ch.ParentEntityId
    line = sdo.Line.find { ln -> ln.LineId==ch.ParentEntityId }
    finer 'Found Line - LineId '+ line.LineId+' HeaderId '+line.HeaderId+' Line ValidFrom = '+line?.PriceValidFrom+' ValidUntil = '+line?.PriceValidUntil
    def hdr = sdo.Header.find { h -> h.HeaderId==line.HeaderId }
    finer 'Found Header - HeaderId '+ hdr?.HeaderId+' Header ValidFrom = '+hdr?.PriceValidFrom+' ValidUntil = '+hdr?.PriceValidUntil

    //assign narrowed validFrom and validUntil to line and header
    line.PriceValidFrom = ( (line?.PriceValidFrom?:validFrom)<=validFrom ) ? validFrom : line?.PriceValidFrom
    hdr.PriceValidFrom = ( (hdr?.PriceValidFrom?:line?.PriceValidFrom)<=validFrom ) ? validFrom : (hdr?.PriceValidFrom?:line?.PriceValidFrom)

    if ( validUntil!=null ) {
        line.PriceValidUntil = ( (line?.PriceValidUntil?:validUntil)>=validUntil ) ? validUntil : line?.PriceValidUntil
        hdr.PriceValidUntil = ( (hdr?.PriceValidUntil?:line?.PriceValidUntil)>=validUntil ) ? validUntil : (hdr?.PriceValidUntil?:line?.PriceValidUntil)
    }
    finer 'Result Valid Dates for Line: ValidFrom = '+line.PriceValidFrom+' ValidUntil = '+line?.PriceValidUntil+' Header: ValidFrom = '+hdr.PriceValidFrom+' ValidUntil = '+hdr?.PriceValidUntil
}

finer 'End Set Final Values'
