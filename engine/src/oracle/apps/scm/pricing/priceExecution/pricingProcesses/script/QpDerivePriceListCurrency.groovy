def serviceParam = sdo.PricingServiceParameter[0]
def strategyName = ''
def foundplist   = 'N'
def terminErr   = ''
def prodGroupUsageCode = 'BASE'

sdo.Header.findAll { !global.invalid.headers.contains(it.HeaderId) }.each { header ->
    finer 'Processing header '+header.HeaderId+'...'
    finer '***** DerivePriceListCurrency [Defining Bindings: Strategy Id]: ' + header.PricingStrategyId
    def mybinding = [
        strategyId: header.PricingStrategyId
        ]
    // validate the Pricing Strategy Id passed form the Header with VO and also filter with effective dates
    def strategyRec = queryVO(PRICING_PROCESS_AM_INST,PRICING_PROCESS_AM,'PricingStrategy1', 'StrategySimpleLookup', mybinding).
    find { isInDateRange(it.StartDate, it.EndDate, header.PriceAsOf) }
    // if Header Strategy not found then it's an Error and mark the Header in Error
    finer '***** DerivePriceListCurrency [Checking if Strategy is valid: Strategy Id]: ' + header.PricingStrategyId
    if ( strategyRec )  {
        finer '***** DerivePriceListCurrency.groovy [ Strategy Record found!, Continuing....]'
        header.AllowCurrencyOverrideFlag = ('Y' == strategyRec.AllowCurrencyOverrideFlag )
        header.DefaultCurrencyCode       = strategyRec.DefaultCurrencyCode
        header.AppliedCurrencyCode       = header.DefaultCurrencyCode
        strategyName                     = strategyRec.Name
        // Raise a warning in case Override Currency is passed but Strategy doesn't All Override Currency
        if ( header.OverrideCurrencyCode!=null && header.OverrideCurrencyCode!=header.AppliedCurrencyCode ) {
            if ( header.AllowCurrencyOverrideFlag ) {
                finer '***** DerivePriceListCurrency.groovy [ Setting the Override Currency if it is allowed....]'
                def currbinding = [
                    StrategyId: header.PricingStrategyId,
                    Currency: header.OverrideCurrencyCode
                    ]
                 // validate the Override Currency Code passed form the Header with VO and also filter with effective dates
                def currRec = queryVO(PRICING_PROCESS_AM_INST,PRICING_PROCESS_AM,'OverrideCurrency1', 'StrategyLookup', currbinding).
                find { isInDateRange(it.StartDate, it.EndDate, header.PriceAsOf) }
                if ( currRec ) {
                    finer '***** DerivePriceListCurrency [ Header Override Currency Found and is valid....]'
                    header.AppliedCurrencyCode = currRec.CurrencyCode
                }
                else    {
                    finer '***** DerivePriceListCurrency.groovy [ WARNING:::Override Currency Code is NOT found!]'  
                    def msg                   = sdo.createDataObject('PricingMessage')
                    msg.PricingMessageId      = new Long((long)(Math.random() * 10))
                    msg.ParentEntityId        = header.HeaderId
                    msg.ParentEntityCode      = ENTITY_CODE_HEADER
                    msg.MessageTypeCode       = MESSAGE_TYPE_WARN
                    msg.MessageName           = 'QP_PDP_STRATEGY_NO_CURR'
                    msg.MessageText           = getFndMessage(APP_SHORT_NAME,msg.MessageName,[STRATEGY_NAME:strategyName])
                }
            }
            else    {
                finer '***** DerivePriceListCurrency [ Overide Cuurecy passed but Stragey does not allow it]'
                 def msg                   = sdo.createDataObject('PricingMessage')
                 msg.PricingMessageId      = new Long((long)(Math.random() * 10))
                 msg.ParentEntityId        = header.HeaderId
                 msg.ParentEntityCode      = ENTITY_CODE_HEADER
                 msg.MessageTypeCode       = MESSAGE_TYPE_WARN
                 msg.MessageName           = 'QP_PDP_CANNOT_OVERRIDE_CURR'
                 msg.MessageText           = getFndMessage(APP_SHORT_NAME,msg.MessageName,[STRATEGY_NAME:strategyName])
            }
        }
        sdo.Line.findAll { line -> line.HeaderId == header.HeaderId && !global.invalid.lines.contains(line.LineId) }.each { line ->
            finer 'Processing Line '+line.LineId+'...'
            def lineId = line.LineId  // assigned it to a local variable so that access is faster
            if ( line.PricingStrategyId!=null && line.PricingStrategyId!=header.PricingStrategyId ) {
                finer '***** DerivePriceListCurrency.groovy [ Line Strategy Id is passed and different from Header....]'
                def linebinding = [
                    strategyId: line.PricingStrategyId
                    ]
                // validate the Pricing Strategy Id passed form the Liner and also filter with effective dates
                def linestrategyRec = queryVO(PRICING_PROCESS_AM_INST,PRICING_PROCESS_AM,'PricingStrategy1', 'StrategySimpleLookup', linebinding).find { isInDateRange(it.StartDate, it.EndDate, line.PriceAsOf) }
                // if Line Strategy not found then skip the line and add a warning message
                finer '***** DerivePriceListCurrency [Checking if Strategy is valid or NOT: Strategy Id]: ' + line.PricingStrategyId
                if ( linestrategyRec )  {
                    finer '***** DerivePriceListCurrency [ LINE Strategy Record found!, Continuing....]'
                    line.AllowCurrencyOverrideFlag = ('Y' == linestrategyRec.AllowCurrencyOverrideFlag  )
                    line.AllowPriceListUpdateFlag  = ('Y' == linestrategyRec.AllowPriceListOverrideFlag )
                    strategyName                   = linestrategyRec.Name
                    global.line[lineId].glConversionTypeCode   = linestrategyRec.DefaultGlConvTypeCode
                    if ( !line.AllowCurrencyOverrideFlag && line.OverrideCurrencyCode!=null && line.OverrideCurrencyCode!= header.DefaultCurrencyCode ) {
                        finer '***** DerivePriceListCurrency [ LINE: Cannot Override Currency...]'
                        def msg                   = sdo.createDataObject('PricingMessage')
                        msg.PricingMessageId      = new Long((long)(Math.random() * 10))
                        msg.ParentEntityCode      = ENTITY_CODE_LINE
                        msg.ParentEntityId        = lineId
                        msg.MessageTypeCode       = MESSAGE_TYPE_WARN
                        msg.MessageName           = 'QP_PDP_CANNOT_OVERRIDE_CURR'
                        msg.MessageText           = getFndMessage(APP_SHORT_NAME,msg.MessageName,[STRATEGY_NAME:strategyName])
                    }
                }
                else    {
                    finer '***** DerivePriceListCurrency [ ERROR:::No Line Strategy Record found!]'
                    def msg                   = sdo.createDataObject('PricingMessage')
                    msg.PricingMessageId      = new Long((long)(Math.random()*10))
                    msg.ParentEntityCode      = ENTITY_CODE_LINE
                    msg.ParentEntityId        = lineId
                    msg.MessageTypeCode       = MESSAGE_TYPE_ERR
                    msg.MessageName           = 'QP_PDP_HDR_STRATEGY_ERROR'
                    msg.MessageText           = getFndMessage(APP_SHORT_NAME,msg.MessageName,[:]) 
                    global.invalid.lines.add(lineId)
                    finer '***** DerivePriceListCurrency [ ERROR:::No Line Strategy:]' +global.invalid.lines
                }
            }
         //   if ((line.ItemType == 'STANDARD' || line.ItemType =='ROOT') { //Check
            if (!global.invalid.lines.contains(lineId)) {
                def strgbinding =   [
                    strategyId: line.PricingStrategyId
                    ]
                def strgRec = queryVO(PRICING_PROCESS_AM_INST, PRICING_PROCESS_AM,'StrategyPriceList1','GetSegmentLists',strgbinding).
                findAll { isInDateRange(it.DetailStartDate, it.DetailEndDate, line.PriceAsOf) && isInDateRange(it.HeaderStartDate, it.HeaderEndDate, line.PriceAsOf) }.
                sort {it.Precedence}
                strgRec.each { row ->
                    finer {'Found strategy Record and Price List Id is: '+row.PriceListId}
                    if (foundplist != 'Y')  {
                        def itemAttr = global.itemAttribute[[line.InventoryItemId, line.InventoryOrganizationId]]
                        def linebindItem =  [
                            businessUnitId: header.SellingBusinessUnitId,
                            checkService: ((itemAttr?.ServiceDurationTypeCode!=null && 'V'==itemAttr.ServiceDurationTypeCode) ? 'Y' : 'N') , 
                            itemId: line.InventoryItemId,
                            lineTypeCode:   line.LineTypeCode,
                            orgId:  line.InventoryOrganizationId,
                            priceListId:    row.PriceListId,
                            prodGroupUsage: prodGroupUsageCode, 
                            uomCode:    line.LineQuantityUOMCode
                            ]
                        finer {'Finding Charges for the Price List :'+row.PriceListId}
                        def plistRec = queryVO(PRICING_PROCESS_AM_INST,PRICING_PROCESS_AM,'SearchPriceListCharge1',null , linebindItem).
                        findAll { isInDateRange(it.StartDate ?:global.line[lineId].pricingDate, it.EndDate ?:global.line[lineId].pricingDate, global.line[lineId].pricingDate) && isInDateRange(it.ItemLevelStartDate ?:global.line[lineId].pricingDate, it.ItemLevelEndDate ?:global.line[lineId].pricingDate, global.line[lineId].pricingDate) }.
                        sort {it.ItemLevelPrecedence}[0]
                        if (! plistRec )    {
                            finer {'NO Charges Found for the Price List :'+row.PriceListId}
                            if ( global.line[lineId].itemType == 'ROOT') {
                            // Check for Item if an item exists for the plist on Strategy
                                def lineItem =  [
                                    businessUnitId: header.SellingBusinessUnitId,
                                    checkService: (itemAttr?.ServiceDurationTypeCode!=null && 'V'==itemAttr.ServiceDurationTypeCode) ? 'Y' : 'N' , 
                                    itemId: line.InventoryItemId,
                                    lineTypeCode: line.LineTypeCode,
                                    priceListId: row.PriceListId,
                                    uomCode: line.LineQuantityUOMCode
                                    ]
                                def plistitemRec = queryVO(PRICING_PROCESS_AM_INST,PRICING_PROCESS_AM,'SearchPriceListItem1',null , lineItem)
                                if (plistitemRec ) {
                                    finer {'Found price list :Item ONLY, no charge '+plistitemRec [0].PriceListItemId}
                                    line.DefaultPriceListId         =  plistitemRec[0].PriceListItemId
                                    line.AppliedPriceListId         = line.DefaultPriceListId
                                    line.DefaultCurrencyCode        = plistitemRec[0].ItemLevelCode
                                    global.line[lineId].defaultPriceListPrecedence = plistRec[0].ItemLevelPrecedenc
                                    global.line[lineId].appliedCurrencyCode        = line.DefaultCurrencyCodee
                                    foundplist        = 'Y'
                                }
                                else    {
                                    // No Item found for the Item Id
                                    finer 'No Price List Item Record found for the Segment Price List form Startegy****'
                                }
                            }  // Check for ROOT Items
                        }
                        else    {
                            // Write the Default Plist
                            finer {'Found price list '+row.PriceListId }
                            finer {'Setting default price list id '+row.PriceListId}
                            line.DefaultPriceListId         = row.PriceListId
                            line.DefaultCurrencyCode        = row.CurrencyCode
                            line.AppliedPriceListId         = line.DefaultPriceListId
                            global.line[lineId].defaultPriceListPrecedence = plistRec.ItemLevelPrecedence
                            global.line[lineId].appliedCurrencyCode        = line.DefaultCurrencyCode
                            foundplist        = 'Y'
                        } 
                    } 
                } //strgRec
            }
            
            if ( !global.invalid.lines.contains(lineId)  && 'Y' == global.line[lineId].termOverrideApplied ) {
                finer '***** DerivePriceListCurrency [ Checking TERM entity for Price List Override.....]'
                sdo.PricingTerm.findAll { termRec -> lineId == termRec.ApplyToEntityId }.each { termRec ->
                    def termsetupbind = [ termId: termRec.TermId ]
                    def termSetup = queryVO(PRICING_PROCESS_AM_INST,PRICING_PROCESS_AM, 'PricingTerm1', 'TermLookup', termsetupbind)
                    finer '***** DerivePriceListCurrency [ Found the TERM entity.....]PriceList :  ' +termSetup.PriceListId
                    if  (terminErr != MESSAGE_TYPE_ERR && termRec.ApplyToEntityCode == ENTITY_CODE_LINE && ( (termSetup.BenefitTypeCode == 'PRICE_LIST_OVERRIDE') || 
                        (termSetup.BenefitTypeCode  ==  'PRICE_LIST_BASED_ADJUSTMENT'   &&  termSetup.PriceListId!=null ))) {
                        def termbind = [
                        priceListId:    termSetup.PriceListId,
                        businessUnitId: header.SellingBusinessUnitId
                        ]
                        def valiTermPL = queryVO(PRICING_PROCESS_AM_INST,PRICING_PROCESS_AM, 'ValidateTermPriceList1', null, termbind).
                        find { isInDateRange(it.StartDate, it.EndDate, global.line[lineId].pricingDate)   }
                        if ( valiTermPL )   {
                            finer {'***** DerivePriceListCurrency: Applying price list '+termSetup.PriceListId+' from pricing term '+termRec.TermId}
                            line.AllowPriceListUpdateFlag   = ('Y'  ==    termSetup.AllowPriceListOverrideFlag)
                            line.AppliedPriceListId         = termSetup.PriceListId
                            global.line[lineId].appliedCurrencyCode    = valiTermPL.CurrencyCode
                            global.line[lineId].termOverrideApplied    = 'Y' 
                        }
                        else    {
                            finer '***** DerivePriceListCurrency [ ERROR:::PL on TERM is Invalid]'
                            def msg                     =   sdo.createDataObject('PricingMessage')
                            msg.PricingMessageId        =   new Long((long)(Math.random()*10))
                            msg.ParentEntityCode        =   ENTITY_CODE_LINE
                            msg.ParentEntityId          =   lineId
                            msg.MessageTypeCode         =   MESSAGE_TYPE_ERR
                            msg.MessageName             =   'QP_PDP_INVALID_TERM_PL_SA'
                            msg.MessageText             =   getFndMessage(APP_SHORT_NAME,msg.MessageName,[:]) 
                            global.invalid.lines.add(lineId)
                                
                            def msg1                    =   sdo.createDataObject('PricingMessage')
                            msg1.PricingMessageId       =   new Long((long)(Math.random()*10))
                            msg1.ParentEntityCode       =   ENTITY_CODE_TERM
                            msg1.ParentEntityId         =   termRec.TermId
                            msg1.MessageTypeCode        =   MESSAGE_TYPE_ERR
                            msg1.MessageName            =   'QP_PDP_TERM_APPLY_PL_ERROR'
                            msg1.MessageText            =   getFndMessage(APP_SHORT_NAME,msg.MessageName,[:]) 
                            terminErr                   =   MESSAGE_TYPE_ERR
                        }
                    }
                } //termRec
            }
            // Warning for Override PL in case Overrride PL is passed but Startegy or Term doesn't allow it.
            if ( (!global.invalid.lines.contains(lineId)) && !(line.AllowPriceListUpdateFlag) && (line.OverridePriceListId!=null))    {
                finer '***** DerivePriceListCurrency [ WARNING for Override PL.....]  ' +global.line[lineId].termOverrideApplied
                line.AppliedPriceListId =null
                if  ( 'Y' == global.line[lineId].termOverrideApplied ) {
                    finer '***** DerivePriceListCurrency [ TERM WARNING.....]:  ' 
                    def msg                   = sdo.createDataObject('PricingMessage')
                    msg.PricingMessageId      = new Long((long)(Math.random()*10))
                    msg.ParentEntityCode      = ENTITY_CODE_LINE
                    msg.ParentEntityId        = lineId
                    msg.MessageTypeCode       = MESSAGE_TYPE_WARN
                    msg.MessageName           = 'QP_PDP_CANNOT_OVERRIDE_PL_TERM'
                    msg.MessageText           = 'QP_PDP_CANNOT_OVERRIDE_PL_TERM' // Need to get a function to get the term name
                }
                else    {
                    def msg                   = sdo.createDataObject('PricingMessage')
                    msg.PricingMessageId      = new Long((long)(Math.random()*10))
                    msg.ParentEntityCode      = ENTITY_CODE_LINE
                    msg.ParentEntityId        = lineId
                    msg.MessageTypeCode       = MESSAGE_TYPE_WARN
                    msg.MessageName           = 'QP_PDP_CANNOT_OVERRIDE_PL'
                    msg.MessageText           = getFndMessage(APP_SHORT_NAME,msg.MessageName,[STRATEGY_NAME:strategyName])                            
                }
            } 
            if (!global.invalid.lines.contains(lineId) )  {
                if  ( line.AppliedPriceListId ==null)   {
                    def msg                   = sdo.createDataObject('PricingMessage')
                    msg.PricingMessageId      = new Long((long)(Math.random()*10))
                    msg.ParentEntityCode      = ENTITY_CODE_LINE
                    msg.ParentEntityId        = lineId
                    msg.MessageTypeCode       = MESSAGE_TYPE_ERR
                    msg.MessageName           = 'QP_PDP_NO_APPLIED_PRICE_LIST'
                    msg.MessageText           = getFndMessage(APP_SHORT_NAME,msg.MessageName,[STRATEGY_NAME:strategyName])
                    global.invalid.lines.add(lineId)
                }     
                if ( global.line[lineId].appliedCurrencyCode !=null )   {
                    // Set the From Currency Code
                    global.line[lineId].fromCurrencyCode = global.line[lineId].appliedCurrencyCode
                }
                if (line.AllowCurrencyOverrideFlag && line.OverrideCurrencyCode!=null && line.OverrideCurrencyCode!=global.line[lineId].appliedCurrencyCode)   {
                    finer '***** DerivePriceListCurrency.groovy [ Setting the LINE Override Currency if it is allowed....]'
                    def linecurrbinding =   [
                        StrategyId: line.PricingStrategyId,
                        Currency: line.OverrideCurrencyCode
                        ]
                    // validate the Override Currency Code passed from the Line with VO and also filter with effective dates
                    def linecurrRec = queryVO(PRICING_PROCESS_AM_INST,PRICING_PROCESS_AM,'OverrideCurrency1', 'StrategyLookup', linecurrbinding).
                    find { isInDateRange(it.StartDate, it.EndDate, line.PriceAsOf) }
                    if ( linecurrRec ) {
                        finer '***** DerivePriceListCurrency [ Line Override Currency Found and is valid....]'
                        global.line[lineId].appliedCurrencyCode = line.OverrideCurrencyCode
                    }
                    else    {
                        finer '***** DerivePriceListCurrency.groovy [ WARNING:::Line Override Currency Code is passed but not Valid!]'  
                        def msg                   = sdo.createDataObject('PricingMessage')
                        msg.PricingMessageId      = new Long((long)(Math.random() * 10))
                        msg.ParentEntityId        = lineId
                        msg.ParentEntityCode      = ENTITY_CODE_LINE
                        msg.MessageTypeCode       = MESSAGE_TYPE_WARN
                        msg.MessageName           = 'QP_PDP_STRATEGY_NO_CURR'
                        msg.MessageText           = getFndMessage(APP_SHORT_NAME,msg.MessageName,[STRATEGY_NAME:strategyName])
                    } 
                }
            }
            // TODO: Currency Conversion needs to be added        
            finer '***** DerivePriceListCurrency [ One Line is completed.....]'    
        } //Each Line
    //StrategyRec
    }
    else    {
        finer '***** DerivePriceListCurrency.groovy [ ERROR:::No Strategy Record found!]'
        serviceParam.OutputStatus = MESSAGE_TYPE_ERR
        def msg                   = sdo.createDataObject('PricingMessage')
        msg.PricingMessageId      = new Long((long)(Math.random() * 10))
        msg.ParentEntityId        = header.HeaderId
        msg.ParentEntityCode      = ENTITY_CODE_HEADER
        msg.MessageTypeCode       = MESSAGE_TYPE_ERR
        msg.MessageName           = 'QP_PDP_HDR_STRATEGY_ERROR'
        msg.MessageText           = getFndMessage(APP_SHORT_NAME,msg.MessageName,[:])
        global.invalid.headers.add(header.HeaderId)
    }
}
