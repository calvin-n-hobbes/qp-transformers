package oracle.apps.scm.pricing.priceExecution.pricingProcesses.engine;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.sql.Date;

import java.util.logging.Level;

import oracle.apps.fnd.applcore.log.AppsLogger;
import oracle.apps.scm.pricing.priceExecution.pricingProcesses.engine.BaseScript;
import oracle.apps.scm.pricing.priceExecution.pricingProcesses.publicUtil.PricingProcessUtil;

class PricingUtil {
    // holder class
    private static class BaseScriptHolder {
        private final static BaseScript INSTANCE = new BaseScript();
    }

    public static BaseScript getBaseScriptInstance() {
        return BaseScriptHolder.INSTANCE;
    }

    /* wrapper methods */
    
    static def getDefaultLanguage() {
        return getBaseScriptInstance().getDefaultLanguage();
    }

    static def getNextId() {
        return getBaseScriptInstance().getNextId();
    }

    static def finest(Object o) {
        getBaseScriptInstance().finest(o);
    }

    static def finer(Object o) {
        getBaseScriptInstance().finer(o);
    }

    static def fine(Object o) {
        getBaseScriptInstance().fine(o);
    }

    static def severe(Object o) {
        getBaseScriptInstance().severe(o);
    }

    public def queryVO(String amConfig, String amName, String voInstance, String vcName, Map<String, Object> bindVars) {
        return getBaseScriptInstance().queryVO(amConfig, amName, voInstance, vcName, bindVars);
    }

    public def isInDateRange(start, end, match) {
        return getBaseScriptInstance().isInDateRange(start, end, match);
    }

    public def getFndMessage (String appShortName, String messageName, Map<String, Object> bindVars) {
        return getBaseScriptInstance().getFndMessage(appShortName, messageName, bindVars);
    }

    /* static util methods */

    static def round(roundingInput, roundingAssignmentTypeCode, unitCode) {
        def roundMap = [:]
        roundMap.roundingOutput = roundingInput.setScale(2, RoundingMode.HALF_UP)
        finer '* rounded to '+roundMap.roundingOutput
        roundMap.differential = roundingInput - roundMap.roundingOutput
        roundMap.ruleId = 10501 //hardcoded CDRM ruleId till implementing rounding rule logic
        return roundMap
    }

    static getPricingMessage(msgName, sourceId) {
        PricingProcessUtil.getPricingMessage(msgName, sourceId)
    }

    static getPricingMessage(msgName, sourceId, inventoryOrgId) {
        PricingProcessUtil.getPricingMessage(msgName, sourceId, inventoryOrgId)
    }

    static getMatrixMessage(msgName, tokens) {
        PricingProcessUtil.getMatrixMessage(msgName, tokens)
    }

    static getManualAdjustmentMessage(msgName, tokens) {
        PricingProcessUtil.getManualAdjustmentMessage(msgName, tokens)
    }

    public static computeUnitAdjustment(adjType, adjUnitPrice, adjAmount, adjBasisUnitPrice) {
        if ( adjType==null || adjAmount==null ) return

        def unitAdjBasisAmount = adjBasisUnitPrice?:adjUnitPrice?:0.0;

        switch (adjType) {
            case 'DISCOUNT_PERCENT':
                -(unitAdjBasisAmount*adjAmount)/100.0
                break
            case 'MARKUP_PERCENT':
                unitAdjBasisAmount*adjAmount/100.0
                break
            case 'DISCOUNT_AMOUNT':
                -adjAmount
                break
            case 'MARKUP_AMOUNT':
                adjAmount;
                break
            case 'PRICE_OVERRIDE':
                adjAmount-adjUnitPrice;
                break
        }
    }
}
