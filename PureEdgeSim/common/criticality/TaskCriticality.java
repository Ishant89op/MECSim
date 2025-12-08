package common.criticality;
import com.mechalikh.pureedgesim.simulationmanager.SimulationManager;
import dag.TaskNode;
import java.util.Map;

public class TaskCriticality {
    protected SimulationManager simulationManager;
    public TaskCriticality(SimulationManager simManager){
        this.simulationManager = simManager;
    }
    public void assignTaskCriticality(Map<Integer, TaskNode> job) {
    }

}

