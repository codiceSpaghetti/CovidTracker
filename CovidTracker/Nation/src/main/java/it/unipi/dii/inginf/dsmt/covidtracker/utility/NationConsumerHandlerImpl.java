package it.unipi.dii.inginf.dsmt.covidtracker.utility;

import com.google.gson.Gson;
import it.unipi.dii.inginf.dsmt.covidtracker.communication.AggregationRequest;
import it.unipi.dii.inginf.dsmt.covidtracker.communication.CommunicationMessage;
import it.unipi.dii.inginf.dsmt.covidtracker.communication.DailyReport;
import it.unipi.dii.inginf.dsmt.covidtracker.intfs.NationConsumerHandler;
import it.unipi.dii.inginf.dsmt.covidtracker.log.CTLogger;
import javafx.util.Pair;

import java.util.List;

public class NationConsumerHandlerImpl implements NationConsumerHandler {

    String myName;
    List<String> childrenAreas;
    boolean[] isReceivedDailyReport;
    DailyReport[] receivedDailyReport;

    public NationConsumerHandlerImpl() {

    }

    @Override
    public void initializeParameters(String nodeName, List<String> childrenAreas) {
        myName = nodeName;
        this.childrenAreas = childrenAreas;
        isReceivedDailyReport = new boolean[childrenAreas.size()];
        receivedDailyReport = new DailyReport[childrenAreas.size()];
        for(int i = 0; i < receivedDailyReport.length; i++) {
            receivedDailyReport[i] = new DailyReport();
        }
    }

    @Override
    public void handleDailyReport(CommunicationMessage cMsg) {
        String area = cMsg.getSenderName();
        String body = cMsg.getMessageBody();

        int index = childrenAreas.indexOf(area);
        CTLogger.getLogger(this.getClass()).info("Ho trovato l'indice " + index + " area: " + area);
        if(index != -1){
            CTLogger.getLogger(this.getClass()).info("dailyReportArrayElement PRE: " + receivedDailyReport[index]);
            isReceivedDailyReport[index] = true;
            receivedDailyReport[index].addAll(new Gson().fromJson(body, DailyReport.class));
            CTLogger.getLogger(this.getClass()).info("dailyReportArrayElement AFTER: " + receivedDailyReport[index]);
        }
    }

    @Override
    public DailyReport getDailyReport() {
        DailyReport aggregatedReport = new DailyReport();
        for(int i = 0; i < receivedDailyReport.length; i++) {
            if(isReceivedDailyReport[i])
                aggregatedReport.addAll(receivedDailyReport[i]);
        }
        resetDailyReports();
        return aggregatedReport;
    }

    @Override
    public Pair<String, CommunicationMessage> handleAggregationRequest(CommunicationMessage cMsg) {
        AggregationRequest aggregationRequested = new Gson().fromJson(cMsg.getMessageBody(), AggregationRequest.class);

        String dest = aggregationRequested.getDestination();
        int index = childrenAreas.indexOf(dest);

        if(index != -1) {
            return new Pair<>(childrenAreas.get(index), cMsg);

        } else if(dest.equals(myName)) {
            return new Pair<>(myName, cMsg);

        } else
            return new Pair<>("flood", cMsg);
    }

    //-------------------------------------------------------------------------------------------------------------

    void resetDailyReports() {
        isReceivedDailyReport = new boolean[childrenAreas.size()];
        receivedDailyReport = new DailyReport[childrenAreas.size()];
        for(int i = 0; i < receivedDailyReport.length; i++) {
            receivedDailyReport[i] = new DailyReport();
        }
    }
}
