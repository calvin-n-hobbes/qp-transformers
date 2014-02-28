package oracle.apps.scm.pricing.priceExecution.pricingProcesses.engine;

import groovy.lang.GroovyObjectSupport;

import oracle.jbo.Row;


public class GroovyRow extends GroovyObjectSupport {
    private Row _row;

    public GroovyRow(Row r) {
        super();
        _row = r;
    }

    @Override
    public Object getProperty(String property) {
        return _row.getAttribute(property);
    }

    @Override
    public void setProperty(String path, Object value) {
        throw new UnsupportedOperationException();
    }

    public Object getAttribute(String attribute) {
        return getProperty(attribute);
    }
}
