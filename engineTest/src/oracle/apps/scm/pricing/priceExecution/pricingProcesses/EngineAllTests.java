package oracle.apps.scm.pricing.priceExecution.pricingProcesses;

import java.sql.SQLException;

import oracle.adf.share.security.authentication.JAASAuthenticationService;

import oracle.apps.common.adfBcUnitTestHelper.xsu.XsuXMLTestDataHelper;


import oracle.apps.financials.commonTest.util.UnitTestHelper;

import oracle.apps.scm.pricing.priceExecution.pricingProcesses.engine.EngineTest;

import oracle.apps.scm.pricing.priceExecution.pricingProcesses.engine.QpSetFinalValuesTest;

import org.jtestcase.JTestCaseException;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;

@RunWith(Suite.class)
@Suite.SuiteClasses( { 
EngineTest.class//, QpSetFinalValuesTest.class
})

public class EngineAllTests {
    private static JAASAuthenticationService jas;
    public EngineAllTests() {
        super();
    }

    @BeforeClass
    public static void setUp() throws SQLException, JTestCaseException {
        jas = new JAASAuthenticationService(); 
        jas.login("PRICING_MANAGER_ALL_BU", "Welcome1");
    }

    @AfterClass
    public static void tearDown() throws Exception {
        
    }
}
