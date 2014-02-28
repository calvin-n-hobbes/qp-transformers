// get item attributes
sdo.Line.findAll { !global.invalid.lines.contains(it.LineId) }.each { line ->
    finer 'Processing line '+line.LineId
    def itemBinding = [item:line.InventoryItemId, org:line.InventoryOrganizationId]
    itemAttr = queryVO(PRICING_PROCESS_AM_INST, PRICING_PROCESS_AM, 'Item1', 'ItemLookup', itemBinding)[0]

    def attrKey = [line.InventoryItemId, line.InventoryOrganizationId]
    if ( itemAttr!=null && !global.itemAttribute.containsKey(attrKey) ) {
        def ia = [:]
        ia.itemNumber = itemAttr.ItemNumber
        ia.serviceDurationType = itemAttr.ServiceDurationTypeCode
        ia.bomItemType = itemAttr.BomItemType
        global.itemAttribute[attrKey] = ia
    }
}

// get party attributes
sdo.Header.findAll { !global.invalid.headers.contains(it.HeaderId) && it.CustomerId!=null }.each { header ->
    finer 'Processing header '+header.HeaderId
    def partyBinding = [BindPartyId:header.CustomerId]
    partyAttr = queryVO(PRICING_PROCESS_AM_INST, PRICING_PROCESS_AM, 'Party1', null, partyBinding)[0]

    if ( partyAttr!=null && !global.partyAttribute.containsKey(header.CustomerId) ) {
        def pa = [:]
        pa.partyName = partyAttr.PartyName
        pa.partyType = partyAttr.PartyType
        global.partyAttribute[header.CustomerId] = pa

        def profileBinding = [partyId:header.CustomerId]
        def profileKey = [header.CustomerId, header.PriceAsOf]
        switch (partyAttr.PartyType) {
            case 'ORGANIZATION':
                // get party organization profile attributes
                def partyOrgProfile = queryVO(PRICING_PROCESS_AM_INST, PRICING_PROCESS_AM, 'PartyOrganizationProfile1', null, profileBinding)
                    .find { isInDateRange(it.EffectiveStartDate, it.EffectiveEndDate, header.PriceAsOf) }

                if ( partyOrgProfile!=null && !global.partyOrgProfile.containsKey(profileKey) ) {
                    def pop = [:]
                    pop.gsaIndicator = partyOrgProfile.GsaIndicatorFlag
                    pop.lineOfBusiness = partyOrgProfile.LineOfBusiness
                    global.partyOrgProfile[profileKey] = pop
                }
                break
            case 'PERSON':
                // get party person profile attributes
                def partyPersonProfile = queryVO(PRICING_PROCESS_AM_INST, PRICING_PROCESS_AM, 'PartyPersonProfile1', null, profileBinding)
                    .find { isInDateRange(it.EffectiveStartDate, it.EffectiveEndDate, header.PriceAsOf) }

                if ( partyPersonProfile!=null && !global.partyOrgProfile.containsKey(profileKey) ) {
                    def ppp = [:]
                    ppp.gender = partyPersonProfile.Gender
                    ppp.maritalStatus = partyOrgProfile.MaritalStatus
                    global.partyPersonProfile[profileKey] = ppp
                }
                break
        }
    }
}
