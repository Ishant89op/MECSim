package common.criticality;

import com.mechalikh.pureedgesim.datacentersmanager.DataCenter;
import com.mechalikh.pureedgesim.simulationmanager.SimulationManager;
import common.Helper;
import dag.TaskNode;

import java.util.Map;

public class CriticalityOnEnergyLateOutDeg extends TaskCriticality {
    static double ENERGY_THRESHOLD = 0.06;
    static double OUT_DEGREE_THRESHOLD = 0.1;
    static double LATENCY_DEADLINE_RATIO_THRESHOLD =0.17;

    public CriticalityOnEnergyLateOutDeg(SimulationManager simManager) {
        super(simManager);
    }

    public void assignTaskCriticality(Map<Integer, TaskNode> job){
        this.assignCriticalityOnEnergyLateOutDeg(job);
    }

    private void assignCriticalityOnEnergyLateOutDeg(Map<Integer, TaskNode> job){
        double total_energy = 0.0;
        double total_out_degree = 0;
        int critical_task = 0;
        DataCenter cloudDc = simulationManager.getDataCentersManager().getCloudDatacentersList().get(0);
        DataCenter edgeDC = simulationManager.getDataCentersManager().getEdgeDatacenterList().get(0);

        for (Map.Entry<Integer, TaskNode> taskInfo : job.entrySet()) {
            TaskNode task = taskInfo.getValue();
            total_out_degree += task.successors.size();
            double energy = Helper.dynamicEnergyConsumption(task.getLength(),
                    task.getEdgeDevice().getMipsPerCore(), task.getEdgeDevice().getMipsPerCore());
            total_energy += energy;
        }

        for (Map.Entry<Integer, TaskNode> taskInfo : job.entrySet()) {
            TaskNode task = taskInfo.getValue();

            double energy = Helper.dynamicEnergyConsumption(task.getLength(),
                    task.getEdgeDevice().getMipsPerCore(), task.getEdgeDevice().getMipsPerCore());
            if(ENERGY_THRESHOLD != 0 && energy/total_energy > ENERGY_THRESHOLD){
                task.setCritical(true);
                critical_task++;
                continue;
            }

            if(OUT_DEGREE_THRESHOLD != 0 && (double) task.successors.size()/total_out_degree > OUT_DEGREE_THRESHOLD){
                task.setCritical(true);
                critical_task++;
                continue;
            }

            double avgLatency = Helper.calculateAverageLatency(task, task.getEdgeDevice(), edgeDC.nodeList.get(0), cloudDc.nodeList.get(0));
            if(LATENCY_DEADLINE_RATIO_THRESHOLD != 0 && avgLatency/task.getMaxLatency() > LATENCY_DEADLINE_RATIO_THRESHOLD){
                critical_task++;
                task.setCritical(true);
            }
        }
    }
}
