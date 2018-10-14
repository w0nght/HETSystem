package testingpackage;

/**
 * @author ace wong on 9/10/2018
 * @project EnergyTradingSystem
 */
import jade.core.Agent;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPAAgentManagement.*;

public class RetailerAgent extends Agent{
	
	protected void setup() {
		// Register agent
		// create service description for Home
		ServiceDescription sd = new ServiceDescription();
		sd.setType("retailer");
		sd.setName(getLocalName());
		// home agent register
		register(sd);
		
		// Subscription handle
		
		// process
	}

	// Method to register service
	void register(ServiceDescription sd) {
		// create new DF Agent Description
		DFAgentDescription dfd = new DFAgentDescription();
		dfd.setName(getAID());
		dfd.addServices(sd);
		try {
			DFService.register(this, dfd);
			System.out.println(getLocalName() + ": is now registered.");
		} catch (FIPAException fe) {
			fe.printStackTrace();
		}
	}

	// get list of agents offering the specified service
	DFAgentDescription[] getService (String serviceType) {
		DFAgentDescription dfd = new DFAgentDescription();
		ServiceDescription sd = new ServiceDescription();
		sd.setType(serviceType);
		dfd.addServices(sd);

		try {
			DFAgentDescription[] result = DFService.search(this, dfd);
			return result;
		} catch (FIPAException fe) {
			fe.printStackTrace();
		}
		return null;
	}

	// Method to deregister retailer agent
	protected void takeDown() {
		try {
			DFService.deregister(this);
			System.out.println(getLocalName() + ": Preparing to die");
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	public void kill() {
		takeDown();
		this.doDelete();
	}
}
