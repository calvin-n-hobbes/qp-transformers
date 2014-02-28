package oracle.apps.scm.pricing.priceExecution.pricingProcesses.engine;

import java.sql.Timestamp;

import java.text.MessageFormat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Locale;

import oracle.apps.fnd.applcore.common.ApplSession;
import oracle.apps.fnd.applcore.common.ApplSessionUtil;
import oracle.apps.fnd.applcore.i18n.util.LanguageMapper;
import oracle.apps.fnd.applcore.log.AppsLogger;
import oracle.apps.fnd.applcore.messages.model.util.Util;

import oracle.jbo.ApplicationModule;
import oracle.jbo.Row;
import oracle.jbo.RowIterator;
import oracle.jbo.ViewCriteria;
import oracle.jbo.ViewObject;
import oracle.jbo.common.StringManager;
import oracle.jbo.server.ViewObjectImpl;

public class DataHelper {
    private static final String AMNAME = "oracle.apps.scm.pricing.priceExecution.pricingProcesses.publicModel.engine.applicationModule.EngineAM";
    private static final String AMCONFIG         = "EngineAMShared";
    private static final String TOKENNAME        = "TokenName";
    private static final String MESSAGEVO        = "FndMessage";
    private static final String MESSAGETOKENVO   = "FndMessageToken";
    private static final String VCMESSAGEVO      = "ByMessageNameAppNameLang";
    private static final String VCMESSAGETOKENVO = "ByMessageNameAppShortName";
    
    /**
     * @return
     * This method returns the default NLS language for the session
     */
    public static String getDefaultLanguage() {
        //check if this is necessary
        ApplicationModule am = ScriptEngine.getInstance().getAmHelper().getAm(AMNAME, AMCONFIG);;
        ApplSession session = ApplSessionUtil.getSession();
            
        if (session != null)          
            return ApplSessionUtil.getNLSLang();
        else
            return "US";
    }

    public static List groovyQueryVO(String amConfig, String amName, String voInstance, String vcName, Map<String, Object> bindVars) {
        List rows = queryVO(amConfig, amName, voInstance, vcName, bindVars);

        return new GroovyRowList(rows);
    }

    public static List<Row> queryVO(String amConfig, String amName, String voInstance, String vcName, Map<String, Object> bindVars) {
        ApplicationModule am = ScriptEngine.getInstance().getAmHelper().getAm(amName, amConfig);

        List<Row> rows = new ArrayList<Row>();
        ViewObjectImpl vo = null;

        vo = (ViewObjectImpl) am.findViewObject(voInstance);

        ViewCriteria vc = null;
        if ( vcName != null ) {
            vo.getViewCriteria(vcName);
            vo.applyViewCriteria(vc);
            vo.setApplyViewCriteriaName(vcName);
        }

        if ( bindVars != null && bindVars.size()>0 ) {
            for ( Map.Entry<String, Object> bindVar : bindVars.entrySet() ) {
                vo.setNamedWhereClauseParam(bindVar.getKey(), bindVar.getValue());
            }
        }

        vo.refreshCollection(null, true, true);

        if ( vo!=null && vo.hasNext() ) {
            Row row = null;
            while ( vo.hasNext() ) {
                row = vo.next();
                rows.add(row);
                if ( AppsLogger.isEnabled(AppsLogger.FINEST) ) {
                    assert row != null;
                    //AppsLogger.write(ScriptEngine.class, "processCode: "+row.getAttribute("ProcessCode"), AppsLogger.FINEST);
                    //AppsLogger.write(ScriptEngine.class, "queryVO: "+row.getAttribute("ChargeDefinitionId"), AppsLogger.FINEST);
                }
            }
        }

        return rows;
    }

    public static boolean isInDateRange(Timestamp start, Timestamp end, Timestamp match) {
        if ( AppsLogger.isEnabled(AppsLogger.FINEST) ) {
            StringBuilder sb = new StringBuilder("isInDateRange: check ");
            sb.append(start==null ? "null" : start);
            sb.append(" < ");
            sb.append(match==null ? "null" : match);
            sb.append(" < ");
            sb.append(end==null ? "null" : end);

            AppsLogger.write(DataHelper.class, "(Thread " + Thread.currentThread().getId() + ") " + sb.toString(), AppsLogger.FINEST);
        }

        if ( match==null ) return false;

        if ( start!=null && start.compareTo(match)>0 ) {
            return false;
        }

        if ( end!=null && end.compareTo(match)<0 ) {
            return false;
        }

        return true;
    }

    private static Object[] untokenize(RowIterator tokensRowIterator, Map<String, Object> params) {
        Object[] result = null;

        // Iterate over each token from MessageTokenVO in order.
        if ( tokensRowIterator!=null ) {
            result = new Object[tokensRowIterator.getRowCount()];
            Row row = tokensRowIterator.first();
            if ( row!=null ) {
                int i = 0;
                do {
                    // Find the token's value in params and push it onto the return array.
                    // If there is no supplied value in params, replace with empty string.
                    String tok = (String) row.getAttribute(TOKENNAME);
                    result[i] = params.get(tok);
                    if ( result[i]==null ) {
                        result[i] = "";
                    }
                    ++i;
                    row = tokensRowIterator.next();
                } while ( row!=null );
            }
        }
        return result;
    }

    private static ViewObject getMessageVO(ApplicationModule am, String appShortName, String messageKey, String lang) {
        ViewObject msgVO = am.findViewObject(MESSAGEVO);
        ((ViewObjectImpl) msgVO).setApplyViewCriteriaName(VCMESSAGEVO);
        msgVO.setNamedWhereClauseParam("appShortName", appShortName);
        msgVO.setNamedWhereClauseParam("messageName", messageKey);
        msgVO.setNamedWhereClauseParam("language", lang);

        ((ViewObjectImpl) msgVO).refreshCollection(null, true, true);

        return msgVO;
    }

    private static ViewObject getMessageTokenVO(ApplicationModule am, String appShortName, String messageKey) {
        ViewObject tokensVO = am.findViewObject(MESSAGETOKENVO);
        ((ViewObjectImpl) tokensVO).setApplyViewCriteriaName(VCMESSAGETOKENVO);
        tokensVO.setNamedWhereClauseParam("appShortName", appShortName);
        tokensVO.setNamedWhereClauseParam("messageName", messageKey);

        ((ViewObjectImpl) tokensVO).refreshCollection(null, true, true);

        return tokensVO;
    }

    public static String getFndMessage(String appShortName, String messageName, Map<String, Object> bindVars) {
        ApplicationModule am = ScriptEngine.getInstance().getAmHelper().getAm(AMNAME, AMCONFIG);
        String lang = LanguageMapper.getOracleLanguageCode(LanguageMapper.APPS, StringManager.getDefaultLocale().getLanguage());
        List<String> messageFieldsList = new ArrayList<String>();
        messageFieldsList.add("MessageText");

        return getFndMessageDetails(am, appShortName, messageName, messageFieldsList, bindVars, lang);
    }

    private static String getFndMessageDetails(ApplicationModule am, String appShortName, String messageKey, List<String> messageFieldsList, Map<String, Object> payloads, String lang) {
        Locale locale = LanguageMapper.getViewLocale(LanguageMapper.ORACLE_CODE, lang);
        String resultMessagePattern = "";
        ViewObject msgVO = getMessageVO(am, appShortName, messageKey, lang);

        if ( msgVO.hasNext() ) {
            Row msgRow = msgVO.next();

            for (String messageField : messageFieldsList) {
                String messagePattern = (String) msgRow.getAttribute(messageField);

                if ( messagePattern!=null && messagePattern.trim().length()>0 && !messagePattern.equalsIgnoreCase("NULL") ) {
                    if ( resultMessagePattern!=null && resultMessagePattern.length()>0 ) {
                        resultMessagePattern += "\n";
                    }
                    resultMessagePattern += messagePattern;
                }
            }
        }

        if ( resultMessagePattern.trim().isEmpty() ) {
            resultMessagePattern = appShortName + "-" + messageKey;
        }

        if ( payloads!=null && payloads.size()>0 ) {
            Object[] payloadArray;
            String fndMessage = null;
            ViewObject tokensVO = getMessageTokenVO(am, appShortName, messageKey);

            payloadArray = untokenize(tokensVO, payloads);

            String messageFormatPattern = Util.createResourceBundleMessage(resultMessagePattern, tokensVO);
            MessageFormat messageFormat = new MessageFormat(messageFormatPattern, locale);
            fndMessage = messageFormat.format(payloadArray);

            return fndMessage;
        }

        return resultMessagePattern;
    }
}
