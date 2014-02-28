def Param = sdo.PricingServiceParameter[0]
Param.OutputStatus = Param.OutputStatus?:'SUCCESS'

sdo.Header.each { header ->
    def headerId = header.HeaderId
    finer '* Found header '+headerId+'...'

    def currentPricingDate = new java.sql.Timestamp(Calendar.instance.time.time)
    header.PriceAsOf = header.PriceAsOf ?: currentPricingDate
    header.PricedOn = currentPricingDate
    global.header[headerId] = [pricingDate : header.TransactionOn ?: currentPricingDate]

    sdo.Line.findAll { line -> line.HeaderId == headerId }.each { line ->
        def lineId = line.LineId
        finer '** Lines with HeaderId  = ' + headerId + ' are LineId = ' + lineId

        line.PriceAsOf = line.PriceAsOf ?: header.PriceAsOf
        line.PricedOn = currentPricingDate
        global.line[lineId] = [pricingDate : line.TransactionOn ?: global.header[headerId].pricingDate]

        invokeStep('QpRetrieveReferenceAttributes', [sdo: sdo])

        if ( !global.line[lineId].itemType ) {
            def itemAttr = global.itemAttribute[[line.InventoryItemId, line.InventoryOrganizationId]]
            def itemType;
            if ( lineId!=line.RootLineId && line.RootLineId!=null ) {
                itemType = 'COMPONENT'
            }
            else if ( itemAttr!= null && 1==itemAttr.BomItemType ) {
                itemType = 'ROOT'
            }
            else {
                itemType = 'STANDARD'
            }
            global.line[lineId].itemType = itemType
        }
    }
}

finer '** Populating Pricing Object Parameters **'
global.pricingObjectParameter = [
    PRICE_LIST_CHARGE: [
        priceElementCode: 'BASE_LIST_PRICE_CHARGE',
        explanationMessageName: 'QP_BASE_LIST_PRICE_CHARGE'
    ],
    PRICE_LIST_TIER: [
        priceElementCode: 'PRICE_LIST_TIER_ADJ',
        explanationMessageName: 'QP_PRICE_LIST_TIER_ADJ'
    ],
    PRICE_LIST_ATTR_ADJ: [
        priceElementCode: 'PRICE_LIST_ATTR_ADJ',
        explanationMessageName: 'QP_PRICE_LIST_ATTR_ADJ'
    ],
    SALES_AGREEMENT: [
        priceElementCode: 'SALES_AGREEMENT_ADJ',
        explanationMessageName: 'QP_SALES_AGREEMENT_ADJ'
    ],
    SALES_AGREEMENT_TIER: [
        priceElementCode: 'SALES_AGREEMENT_TIER_ADJ',
        explanationMessageName: 'QP_SALES_AGREEMENT_TIER_ADJ'
    ],
    SALES_AGREEMENT_ATTR_ADJ: [
        priceElementCode: 'SALES_AGREEMENT_ATTR_ADJ',
        explanationMessageName: 'QP_SALES_AGREEMENT_ATTR_ADJ'
    ],
    DISCOUNT_LINE: [
        priceElementCode: 'DISCOUNT_ADJ',
        explanationMessageName: 'QP_DISCOUNT_ADJ'
    ],
    DISCOUNT_LINE_TIER: [
        priceElementCode: 'DISCOUNT_LINE_TIER_ADJ',
        explanationMessageName: 'QP_DISCOUNT_TIER_ADJ'
    ],
    DISCOUNT_LINE_ATTR_ADJ: [
        priceElementCode: 'DISCOUNT_LINE_ATTR_ADJ',
        explanationMessageName: 'QP_DISCOUNT_LINE_ATTR_ADJ'
    ],
    ROUNDING_ADJ: [
        priceElementCode: 'ROUNDING_ADJ',
        explanationMessageName: 'QP_ROUNDING_ADJUSTMENT'
    ]
]
