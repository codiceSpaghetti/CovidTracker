package it.unipi.dii.inginf.dsmt.covidtracker.regions;

import it.unipi.dii.inginf.dsmt.covidtracker.ejbs.GenericRegionNode;
import it.unipi.dii.inginf.dsmt.covidtracker.intfs.regionInterfaces.RegionUmbria;
import it.unipi.dii.inginf.dsmt.covidtracker.persistence.JavaErlServicesClientImpl;
import it.unipi.dii.inginf.dsmt.covidtracker.persistence.KVManagerImpl;
import org.json.simple.parser.ParseException;

import javax.annotation.PostConstruct;
import javax.ejb.Stateful;
import java.io.IOException;

@Stateful(name = "UmbriaRegionEJB")
public class UmbriaRegionNode extends GenericRegionNode implements RegionUmbria {
    @PostConstruct
    public void init(){
        try {
            myName = "umbria";
            myDestinationName = myHierarchyConnectionsRetriever.getMyDestinationName(myName);
            myAreaDestinationName = myHierarchyConnectionsRetriever.getParentDestinationName(myName);

            myKVManager = new KVManagerImpl(myName);
            myKVManager.deleteAllClientRequest();
            myErlangClient = new JavaErlServicesClientImpl(myName);

            myMessageHandler.initializeParameters(myName, myDestinationName, myAreaDestinationName);

            setQueueConsumer(myDestinationName);
            startReceivingLoop();
        } catch (ParseException | IOException e) {
            e.printStackTrace();
        }
    }
}
