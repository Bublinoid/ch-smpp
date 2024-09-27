package ru.bublinoid.chsmpp;

import com.cloudhopper.smpp.SmppConstants;
import com.cloudhopper.smpp.pdu.DeliverSm;
import com.cloudhopper.smpp.pdu.PduResponse;

import com.cloudhopper.smpp.type.SmppInvalidArgumentException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import ru.bublinoid.chsmpp.service.SmppService;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class SmppServiceTest {

    private SmppService smppService;

    @BeforeEach
    void setUp() {
        smppService = Mockito.spy(new SmppService());
        Mockito.doNothing().when(smppService).connect();
        smppService.connect();
    }

    @Test
    void testHandleDeliverSmDeliveryReceipt() throws SmppInvalidArgumentException {
        DeliverSm deliverSm = new DeliverSm();
        deliverSm.setEsmClass(SmppConstants.ESM_CLASS_MT_SMSC_DELIVERY_RECEIPT);
        deliverSm.setShortMessage("Delivery receipt message".getBytes(StandardCharsets.UTF_8));

        PduResponse response = smppService.handleDeliverSm(deliverSm);

        assertNotNull(response);
        assertEquals(deliverSm.createResponse().getCommandId(), response.getCommandId());
    }

    @Test
    void testHandleDeliverSmMobileOriginatedMessage() throws SmppInvalidArgumentException {
        DeliverSm deliverSm = new DeliverSm();
        deliverSm.setEsmClass((byte) 0);
        deliverSm.setShortMessage("Mobile originated message".getBytes(StandardCharsets.UTF_8));

        PduResponse response = smppService.handleDeliverSm(deliverSm);

        assertNotNull(response);
        assertEquals(deliverSm.createResponse().getCommandId(), response.getCommandId());
    }

    @Test
    void testSendMultipleSubmitSmWithoutAwaitingResponse() {
        String from = "12345";
        String to = "54321";
        String[] messages = new String[10];

        for (int i = 0; i < 10; i++) {
            messages[i] = "Message " + i;
        }
        smppService.sendMultipleMessages(messages, from, to);

        assertEquals(10, smppService.getSentMessageCount());
    }

}
