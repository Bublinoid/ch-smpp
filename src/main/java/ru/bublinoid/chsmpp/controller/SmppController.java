package ru.bublinoid.chsmpp.controller;


import com.cloudhopper.smpp.SmppConstants;
import com.cloudhopper.smpp.pdu.DeliverSm;
import com.cloudhopper.smpp.type.Address;
import com.cloudhopper.smpp.type.SmppInvalidArgumentException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.bublinoid.chsmpp.service.SmppService;


import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/sms")
public class SmppController {
    private static final Logger logger = LoggerFactory.getLogger(SmppController.class);

    private final SmppService smppService;

    @Autowired
    public SmppController(SmppService smppService) {
        this.smppService = smppService;
    }

    @PostMapping("/send")
    public String sendSms(
            @RequestParam String message,
            @RequestParam String from,
            @RequestParam String to,
            @RequestParam(required = false, defaultValue = "1") byte registeredDelivery) {
        try {
            smppService.sendSms(message, from, to, registeredDelivery);
            return "SMS sent successfully";
        } catch (Exception e) {
            logger.error("Error sending message", e);
            return "Failed to send SMS: " + e.getMessage();
        }
    }

    @PostMapping("/sendMultiple")
    public String sendMultipleSms(
            @RequestParam String[] messages,
            @RequestParam String from,
            @RequestParam String to) {
        try {
            smppService.sendMultipleMessages(messages, from, to);
            return "SMS multiple sent successfully";
        } catch (Exception e) {
            logger.error("Error sending multiple message", e);
            return "Failed to send multiple SMS: " + e.getMessage();
        }
    }

    @PostMapping("/sendAsync")
    public String sendAsyncSms(
            @RequestParam int count,
            @RequestParam String from,
            @RequestParam String to) {
        try {
            String[] messages = new String[count];
            for (int i = 0; i < count; i++) {
                messages[i] = "Message " + i;
            }

            smppService.sendMultipleMessages(messages, from, to);
            return count + " messages sent";
        } catch (Exception e) {
            logger.error("Error sending async messages", e);
            return "Failed to send async messages: " + e.getMessage();
        }
    }


    @PostMapping("/deliver")
    public String simulateDeliverSm(
            @RequestParam String sourceAddress,
            @RequestParam String destinationAddress,
            @RequestParam String message,
            @RequestParam int esmClass) {
        try {
            DeliverSm deliverSm = new DeliverSm();
            deliverSm.setSourceAddress(new Address(SmppConstants.TON_INTERNATIONAL, SmppConstants.NPI_ISDN, sourceAddress));
            deliverSm.setDestAddress(new Address(SmppConstants.TON_INTERNATIONAL, SmppConstants.NPI_ISDN, destinationAddress));
            deliverSm.setShortMessage(message.getBytes(StandardCharsets.UTF_8));
            deliverSm.setEsmClass((byte) esmClass);

            smppService.handleDeliverSm(deliverSm);
            return "DeliverSm sent successfully";
        } catch (SmppInvalidArgumentException e) {
            throw new RuntimeException(e);
        }
    }

    @PostMapping("/sendBulk")
    public String sendBulkSms(
            @RequestParam int count,
            @RequestParam String from,
            @RequestParam String to) {
        try {
            String[] messages = new String[count];
            for (int i = 0; i < count; i++) {
                messages[i] = "Message " + i;
            }

            smppService.sendBulkMessages(messages, from, to);
            return count + " messages sent using windowing mechanism";
        } catch (Exception e) {
            logger.error("Error sending bulk messages", e);
            return "Failed to send bulk SMS: " + e.getMessage();
        }
    }
}
