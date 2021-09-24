package it.unipi.dii.inginf.dsmt.covidtracker.ejbs;

import it.unipi.dii.inginf.dsmt.covidtracker.intfs.RegionConsumerHandler;
import it.unipi.dii.inginf.dsmt.covidtracker.communication.*;
import it.unipi.dii.inginf.dsmt.covidtracker.enums.MessageType;
import it.unipi.dii.inginf.dsmt.covidtracker.intfs.*;
import it.unipi.dii.inginf.dsmt.covidtracker.log.CTLogger;
import it.unipi.dii.inginf.dsmt.covidtracker.persistence.JavaErlServicesClientImpl;
import javafx.util.Pair;
import com.google.gson.Gson;

import javax.annotation.Resource;
import javax.ejb.EJB;
import javax.enterprise.concurrent.ManagedExecutorService;
import javax.jms.*;
import javax.jms.Queue;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class GenericRegionNode{
    final static String QC_FACTORY_NAME = "jms/__defaultConnectionFactory";

    @Resource(mappedName = "concurrent/__defaultManagedExecutorService")
    protected ManagedExecutorService executor;

    @EJB private Producer myProducer;
    @EJB protected RegionConsumerHandler myMessageHandler;
    @EJB protected HierarchyConnectionsRetriever myHierarchyConnectionsRetriever;
    protected JavaErlServicesClient myErlangClient;

    protected KVManager myKVManager;

    private Map<String, List<DataLog>> dataLogs = new HashMap<>(); //logs received from web servers, the key is the day of the dataLog (format dd/MM/yyyy)
    //and the value is the list of logs received in that day

    protected String myName;
    protected String myDestinationName;
    protected String myAreaDestinationName;

    protected JMSConsumer myQueueConsumer;

    private final Gson gson = new Gson();

    protected void setQueueConsumer(final String QUEUE_NAME) {
        try{
            Context ic = new InitialContext();
            Queue myQueue= (Queue)ic.lookup(QUEUE_NAME);
            QueueConnectionFactory qcf = (QueueConnectionFactory)ic.lookup(QC_FACTORY_NAME);
            myQueueConsumer = qcf.createContext().createConsumer(myQueue);
            //qcf.createContext().createConsumer(myQueue).setMessageListener(this);
        }
        catch (final NamingException e) {
            e.printStackTrace();
        }
    }

    public String readReceivedMessages() { return myKVManager.getAllClientRequest(); }

    protected void startReceivingLoop() {
        final Runnable receivingLoop = new Runnable() {
            @Override
            public void run() {
                try {
                    while (!Thread.currentThread().isInterrupted()) {
                        Message inMsg = myQueueConsumer.receive();
                        handleMessage(inMsg);
                    }
                } catch (Exception e) {
                    StringWriter sw = new StringWriter();
                    PrintWriter pw = new PrintWriter(sw);
                    e.printStackTrace(pw);
                    CTLogger.getLogger(this.getClass()).info("Eccezione: " + sw.toString());
                }
            }
        };
        executor.execute(receivingLoop);
    }

    public GenericRegionNode(){ }

    public void handleMessage(Message msg) {
        if (msg instanceof ObjectMessage) {
            try {
                CommunicationMessage cMsg = (CommunicationMessage) ((ObjectMessage) msg).getObject();
                myKVManager.addClientRequest(cMsg.toString());
                Pair<String, CommunicationMessage> messageToSend;

                switch (cMsg.getMessageType()) {
                    case NO_ACTION_REQUEST:
                        break;
                    case REGISTRY_CLOSURE_REQUEST:
                        closeRegister(myAreaDestinationName);
                        break;
                    case AGGREGATION_REQUEST:
                        messageToSend = myMessageHandler.handleAggregationRequest(cMsg);
                        if (messageToSend.getValue().getMessageType() == MessageType.AGGREGATION_REQUEST) {
                            myProducer.enqueue(messageToSend.getKey(), messageToSend.getValue(), msg.getJMSReplyTo());
                        } else
                            handleAggregation((ObjectMessage) msg);
                        break;
                    case NEW_DATA:
                        saveDataLog(gson.fromJson(cMsg.getMessageBody(), DataLog.class));
                        break;
                    default:
                        break;
                }
            } catch (Exception e) {
                StringWriter sw = new StringWriter();
                PrintWriter pw = new PrintWriter(sw);
                e.printStackTrace(pw);
                CTLogger.getLogger(this.getClass()).info("Eccezione: " + sw.toString());
            }
        }
    }

    // --------------------------------------------------------

    private void closeRegister(String destination){
        DailyReport dailyReport = new DailyReport();
        String currentDate = getCurrentDate();

        double numSwab = myKVManager.getDailyReport(currentDate, "swab");
        double numPositive = myKVManager.getDailyReport(currentDate, "positive");
        double numNegative = myKVManager.getDailyReport(currentDate, "negative");
        double numDead = myKVManager.getDailyReport(currentDate, "dead");


        if (numSwab == -1) numSwab = 0;
        if (numPositive == -1) numPositive = 0;
        if (numNegative == -1) numNegative = 0;
        if (numDead == -1) numDead = 0;

        dailyReport.addTotalSwab((int)numSwab);
        dailyReport.addTotalPositive((int)numPositive);
        dailyReport.addTotalNegative((int)numNegative);
        dailyReport.addTotalDead((int)numDead);

        deleteDailyReport(currentDate);
        myKVManager.addDailyReport(dailyReport);

        CommunicationMessage outMsg = new CommunicationMessage();
        outMsg.setSenderName(myName);
        outMsg.setMessageType(MessageType.DAILY_REPORT);
        outMsg.setMessageBody(gson.toJson(dailyReport));

        CTLogger.getLogger(this.getClass()).info("Sending " + myName + " Daily Report to " + destination + "\nReport: " + outMsg.toString());
        myProducer.enqueue(destination, outMsg);
    }

    private void handleAggregation(ObjectMessage msg) {
        try {
            CommunicationMessage cMsg = (CommunicationMessage) ((ObjectMessage) msg).getObject();
            AggregationRequest request = gson.fromJson(cMsg.getMessageBody(), AggregationRequest.class);
            double result = 0.0;

            CommunicationMessage outMsg = new CommunicationMessage();
            outMsg.setMessageType(MessageType.AGGREGATION_RESPONSE);
            outMsg.setSenderName(myName);
            AggregationResponse response = new AggregationResponse(request);

            if (request.getStartDay().equals(request.getLastDay())) {
                result = myKVManager.getDailyReport(request.getLastDay(), request.getType());
                if(result == -1.0 || request.getOperation().equals("standard_deviation") || request.getOperation().equals("variance"))
                    result = 0.0;

            } else {
                result = myKVManager.getAggregation(request);
                if (result == -1.0) {
                    try {
                        result = myErlangClient.computeAggregation(
                                request.getOperation(),
                                myKVManager.getDailyReportsInAPeriod(request.getStartDay(), request.getLastDay(), request.getType())
                        );
                        myKVManager.saveAggregation(request, result);
                    } catch (Exception e) {
                        StringWriter sw = new StringWriter();
                        PrintWriter pw = new PrintWriter(sw);
                        e.printStackTrace(pw);
                        result = 0.0;
                    }
                }
            }

            response.setResult(result);
            outMsg.setMessageBody(gson.toJson(response));

            // send the reply directly to te requester
            myProducer.enqueue(msg.getJMSReplyTo(), outMsg);
        } catch (Exception e) {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            e.printStackTrace(pw);
            CTLogger.getLogger(this.getClass()).info("Eccezione: " + sw.toString());
        }
    }
    private void saveDataLog(DataLog dataLog){
        String currentDate = getCurrentDate();
        DailyReport dailyReport = new DailyReport();
        double numSwab = myKVManager.getDailyReport(currentDate, "swab");
        double numPositive = myKVManager.getDailyReport(currentDate, "positive");
        double numNegative = myKVManager.getDailyReport(currentDate, "negative");
        double numDead = myKVManager.getDailyReport(currentDate, "dead");

        deleteDailyReport(currentDate);

        if (numSwab == -1) numSwab = 0;
        if (numPositive == -1) numPositive = 0;
        if (numNegative == -1) numNegative = 0;
        if (numDead == -1) numDead = 0;

        if (dataLog.getType().equals("swab"))
            numSwab += dataLog.getQuantity();
        if (dataLog.getType().equals("positive"))
            numPositive += dataLog.getQuantity();
        if (dataLog.getType().equals("negative"))
            numNegative += dataLog.getQuantity();
        if (dataLog.getType().equals("dead"))
            numDead += dataLog.getQuantity();

        dailyReport.addTotalSwab((int)numSwab);
        dailyReport.addTotalPositive((int)numPositive);
        dailyReport.addTotalNegative((int)numNegative);
        dailyReport.addTotalDead((int)numDead);

        myKVManager.addDailyReport(dailyReport);
    }

    private void deleteDailyReport(String day){
        myKVManager.deleteDailyReport(day, "swab");
        myKVManager.deleteDailyReport(day, "positive");
        myKVManager.deleteDailyReport(day, "negative");
        myKVManager.deleteDailyReport(day, "dead");

    }

    private String getCurrentDate(){
        LocalDate localDate = LocalDate.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        return localDate.format(formatter);
    }
}
