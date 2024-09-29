package ru.bublinoid.chsmpp.service;

import com.cloudhopper.smpp.SmppBindType;
import com.cloudhopper.smpp.SmppConstants;
import com.cloudhopper.smpp.SmppSession;
import com.cloudhopper.smpp.impl.DefaultSmppClient;
import com.cloudhopper.smpp.pdu.DeliverSm;
import com.cloudhopper.smpp.pdu.PduRequest;
import com.cloudhopper.smpp.pdu.PduResponse;
import com.cloudhopper.smpp.pdu.SubmitSm;
import com.cloudhopper.smpp.pdu.SubmitSmResp;
import com.cloudhopper.smpp.type.*;
import com.cloudhopper.smpp.SmppSessionConfiguration;
import com.cloudhopper.smpp.impl.DefaultSmppSessionHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;


@Service
public class SmppService {

    @Value("${smpp.server}")
    private String server;
    @Value("${smpp.port}")
    private int port;
    @Value("${smpp.systemId}")
    private String systemId;
    @Value("${smpp.password}")
    private String password;

    private final DefaultSmppClient smppClient;
    private SmppSession session;
    private final ExecutorService executorService = Executors.newFixedThreadPool(10);
    private final ConcurrentHashMap<String, Long> sentMessages = new ConcurrentHashMap<>();
    private final Logger logger = LoggerFactory.getLogger(SmppService.class);

    public SmppService() {
        smppClient = new DefaultSmppClient();
    }

    public void connect() {
        SmppSessionConfiguration config = new SmppSessionConfiguration();
        config.setType(SmppBindType.TRANSCEIVER);
        config.setHost(server);
        config.setPort(port);
        config.setSystemId(systemId);
        config.setPassword(password);

        try {
            session = smppClient.bind(config, new DefaultSmppSessionHandler() {
                @Override
                public PduResponse firePduRequestReceived(PduRequest pduRequest) {
                    if (pduRequest instanceof DeliverSm) {
                        return handleDeliverSm((DeliverSm) pduRequest);
                    }
                    return null;
                }
            });
            logger.info("Connected to SMPP server");
        } catch (Exception e) {
            throw new RuntimeException("Failed to connect to SMPP server", e);
        }
    }

    public void disconnect() {
        if (session != null && session.isBound()) {
            session.unbind(5000);
            session.destroy();
        }
    }

    public void reconnect() {
        logger.info("Attempting to reconnect...");
        try {
            disconnect();
            connect();
            logger.info("Reconnected to SMPP server");
        } catch (Exception e) {
            logger.error("Failed to reconnect to SMPP server", e);
        }
    }


    public void sendSms(String message, String from, String to, byte registeredDelivery) {
        if (session == null || !session.isBound()) {
            connect();
        }

        try {
            SubmitSm submitSm = new SubmitSm();
            submitSm.setSourceAddress(new Address(SmppConstants.TON_INTERNATIONAL, SmppConstants.NPI_ISDN, from));
            submitSm.setDestAddress(new Address(SmppConstants.TON_INTERNATIONAL, SmppConstants.NPI_ISDN, to));
            submitSm.setShortMessage(message.getBytes());
            submitSm.setRegisteredDelivery(registeredDelivery);

            SubmitSmResp submitSmResp = session.submit(submitSm, 30000);

            logger.info("Message sent, message id: {}", submitSmResp.getMessageId());

        } catch (SmppChannelException e) {
            logger.error("Channel issue detected. Attempting to reconnect.", e);
            reconnect();
            sendSms(message, from, to, registeredDelivery);
        } catch (SmppTimeoutException | SmppInvalidArgumentException e) {
            throw new RuntimeException("Failed to send SMS due to timeout or invalid argument", e);
        } catch (RecoverablePduException | UnrecoverablePduException | InterruptedException e) {
            throw new RuntimeException(e);
        }
        sentMessages.put(message, System.currentTimeMillis());
    }


    public PduResponse handleDeliverSm(DeliverSm deliverSm) {
        if (deliverSm == null) {
            return null;
        }

        try {
            if ((deliverSm.getEsmClass() & SmppConstants.ESM_CLASS_MT_SMSC_DELIVERY_RECEIPT) == SmppConstants.ESM_CLASS_MT_SMSC_DELIVERY_RECEIPT) {
                logger.info("Received delivery report: {}", new String(deliverSm.getShortMessage()));
            } else {
                logger.info("Received MO message: {}", new String(deliverSm.getShortMessage()));
            }
            return deliverSm.createResponse();
        } catch (Exception e) {
            logger.error("Error handling DeliverSm", e);
            return null;
        }
    }

    public void sendMultipleMessages(String[] messages, String from, String to) {
        if (session == null || !session.isBound()) {
            connect();
        }
        long startTime = System.currentTimeMillis();

        for (String message : messages) {
            executorService.submit(() -> {
                try {
                    logger.info("Sending message: {}", message);
                    SubmitSm submitSm = new SubmitSm();
                    submitSm.setSourceAddress(new Address(SmppConstants.TON_INTERNATIONAL, SmppConstants.NPI_ISDN, from));
                    submitSm.setDestAddress(new Address(SmppConstants.TON_INTERNATIONAL, SmppConstants.NPI_ISDN, to));
                    submitSm.setShortMessage(message.getBytes());
                    submitSm.setRegisteredDelivery(SmppConstants.REGISTERED_DELIVERY_SMSC_RECEIPT_REQUESTED);

                    session.sendRequestPdu(submitSm, 30000,false);
                    logger.info("Message sent: {}", message);

                    sentMessages.put(message, System.currentTimeMillis());
                } catch (SmppChannelException e) {
                    logger.error("Channel issue detected. Attempting to reconnect.", e);
                    reconnect();
                } catch (Exception e) {
                    logger.error("Faild to send message: {}", message, e);
                }
            });
        }
        long endTime = System.currentTimeMillis();
        logger.info("All messages dispatched in {} ms", endTime - startTime);

    }

    public void sendBulkMessages(String[] messages, String from, String to) {
        if (session == null || !session.isBound()) {
            connect();
        }

        long startTime = System.currentTimeMillis();

        session.getConfiguration().setWindowSize(10);

        for (String message : messages) {
            executorService.submit(() -> {
                try {
                    logger.info("Sending message: {}", message);
                    SubmitSm submitSm = new SubmitSm();
                    submitSm.setSourceAddress(new Address(SmppConstants.TON_INTERNATIONAL, SmppConstants.NPI_ISDN, from));
                    submitSm.setDestAddress(new Address(SmppConstants.TON_INTERNATIONAL, SmppConstants.NPI_ISDN, to));
                    submitSm.setShortMessage(message.getBytes());
                    submitSm.setRegisteredDelivery(SmppConstants.REGISTERED_DELIVERY_SMSC_RECEIPT_REQUESTED);

                    session.sendRequestPdu(submitSm, 30000, false);
                    logger.info("Message sent: {}", message);

                    sentMessages.put(message, System.currentTimeMillis());
                } catch (Exception e) {
                    logger.error("Failed to send message: {}", message, e);
                }
            });
        }

        long endTime = System.currentTimeMillis();
        logger.info("All messages dispatched in {} ms", endTime - startTime);
    }

    public void sendLongSms(String message, String from, String to, byte registeredDelivery) {
        if (session == null || !session.isBound()) {
            connect();
        }

        try {
            byte[] messageBytes = message.getBytes(StandardCharsets.UTF_8);
            int maxSegmentSize = 153;

            if (messageBytes.length > maxSegmentSize) {
                byte[][] messageParts = splitMessage(messageBytes, maxSegmentSize);

                for (int i = 0; i < messageParts.length; i++) {
                    SubmitSm submitSm = new SubmitSm();
                    submitSm.setSourceAddress(new Address(SmppConstants.TON_INTERNATIONAL, SmppConstants.NPI_ISDN, from));
                    submitSm.setDestAddress(new Address(SmppConstants.TON_INTERNATIONAL, SmppConstants.NPI_ISDN, to));
                    submitSm.setShortMessage(messageParts[i]);
                    submitSm.setRegisteredDelivery(registeredDelivery);

                    SubmitSmResp submitSmResp = session.submit(submitSm, 30000);
                    logger.info("Message part {} sent, message id: {}", (i + 1), submitSmResp.getMessageId());
                }
            } else {
                sendSms(message, from, to, registeredDelivery);
            }
        } catch (Exception e) {
            logger.error("Failed to send long SMS", e);
        }
    }

    /**
     * Разделение длинного сообщения на части с UDH для сегментирования.
     */
    private byte[][] splitMessage(byte[] message, int maxSegmentSize) {
        int numSegments = (int) Math.ceil((double) message.length / maxSegmentSize);
        byte[][] segments = new byte[numSegments][];

        for (int i = 0; i < numSegments; i++) {
            int segmentSize = Math.min(maxSegmentSize, message.length - i * maxSegmentSize);
            byte[] segment = new byte[segmentSize + 6];

            segment[0] = 0x05;
            segment[1] = 0x00;
            segment[2] = 0x03;
            segment[3] = (byte) 0x01;
            segment[4] = (byte) numSegments;
            segment[5] = (byte) (i + 1);

            System.arraycopy(message, i * maxSegmentSize, segment, 6, segmentSize);
            segments[i] = segment;
        }

        return segments;
    }



    public int getSentMessageCount() {
        return sentMessages.size();
    }

    public void shutdownExecutor() {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}

