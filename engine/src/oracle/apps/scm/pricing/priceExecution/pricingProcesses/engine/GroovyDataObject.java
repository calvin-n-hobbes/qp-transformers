package oracle.apps.scm.pricing.priceExecution.pricingProcesses.engine;

import commonj.sdo.DataObject;

import groovy.lang.GroovyObjectSupport;

import oracle.jbo.common.service.types.AmountType;
import oracle.jbo.common.service.types.MeasureType;

import java.math.BigDecimal;
import java.math.BigInteger;

import java.util.List;

/*
 * A composite instance of <code>DataObject</code> that implements the
 * <code>getProperty</code> and <code>setProperty</code> methods of
 * <code>GroovyObject</code> so that Groovy can access SDOs with standard
 * dot notation.
 */
public class GroovyDataObject extends GroovyObjectSupport {
    DataObject sdo;

    public GroovyDataObject(DataObject d) {
        super();
        sdo = d;
    }

    public DataObject getDataObject() {
        return sdo;
    }

    private static String firstCharToLowerCase(String str) {
        return str.replaceFirst(str.substring(0,1), str.substring(0,1).toLowerCase());
    }

    private static Object convertValueToBigDecimal(Object value) {
        if ( value!=null ) {
            if ( value instanceof BigInteger ) {
                value = new BigDecimal((BigInteger) value);
            }
            else if ( value instanceof Double ) {
                value = new BigDecimal((Double) value);
            }
            else if ( value instanceof Integer ) {
                value = new BigDecimal((Integer) value);
            }
            else if ( value instanceof Long ) {
                value = new BigDecimal((Long) value);
            }
            else if ( value instanceof String ) {
                value = new BigDecimal((String) value);
            }
        }
        return value;
    }

    @Override
    public Object getProperty(String property) {
        Object result = null;
        String path = property;

        if ( sdo instanceof AmountType || sdo instanceof MeasureType ) {
            path = firstCharToLowerCase(property);
        }

        result = sdo.get(sdo.getInstanceProperty(path));

        if ( result instanceof List ) {
            result = new GroovyDataObjectList((List) result);
        }
        else if ( result instanceof AmountType || result instanceof MeasureType ) {
            result = new GroovyDataObject((DataObject) result);
        }

        return result;
    }

    @Override
    public void setProperty(String path, Object value) {
        if ( sdo instanceof AmountType ) {
            if ( "value".equalsIgnoreCase(path) ) {
                value = convertValueToBigDecimal(value);
                ((AmountType) sdo).setValue((BigDecimal) value);
            }
            else if ( "currencycode".equalsIgnoreCase(path) ) {
                ((AmountType) sdo).setCurrencyCode((String) value);
            }
        }
        else if ( sdo instanceof MeasureType ) {
            if ("value".equalsIgnoreCase(path)) {
                value = convertValueToBigDecimal(value);
                ((MeasureType) sdo).setValue((BigDecimal) value);
            }
            else if ( "unitcode".equalsIgnoreCase(path) ) {
                ((MeasureType) sdo).setUnitCode((String) value);
            }
        }
        else {
            sdo.set(path, value);
        }
    }

    public GroovyDataObject createDataObject(String name) {
        return new GroovyDataObject(sdo.createDataObject(name));
    }

    /*
     * Emulates <code>commonj.sdo.DataObject</code> method <code>get(String)</code>.
     */
    public Object get(String path) {
        Object result = sdo.get(path);

        if ( result instanceof List ) {
            result = new GroovyDataObjectList((List) result);
        }
        else if ( result instanceof DataObject ) {
            result = new GroovyDataObject((DataObject) result);
        }

        return result;
    }
    
    /*
     * Emulates <code>commonj.sdo.DataObject</code> method <code>delete</code>.
     */
    public void delete() {
        sdo.detach();
    }
}
