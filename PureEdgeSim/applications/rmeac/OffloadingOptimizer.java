package applications.rmeac;

import com.mechalikh.pureedgesim.datacentersmanager.ComputingNode;
import com.mechalikh.pureedgesim.datacentersmanager.DataCenter;
import com.mechalikh.pureedgesim.simulationmanager.SimulationManager;
import common.Helper;
import dag.TaskNode;
import org.moeaframework.Executor;
import org.moeaframework.core.NondominatedPopulation;
import org.moeaframework.core.Problem;
import org.moeaframework.core.Solution;
import org.moeaframework.core.variable.EncodingUtils;
import org.moeaframework.problem.AbstractProblem;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class OffloadingOptimizer {
    // Constants
    private static final int POPULATION_SIZE = 100;
    private static final int MAX_GENERATIONS = 100;

    private SimulationManager simulationManager;

    class FlatEdgeServer {
        int dcIndex;
        int serverIndex;
        ComputingNode node;
        public FlatEdgeServer(int d, int s, ComputingNode n) { dcIndex = d; serverIndex = s; node = n; }
    }
    private List<FlatEdgeServer> flatEdgeServers;

    public OffloadingOptimizer(SimulationManager simManager){
        this.simulationManager = simManager;
        this.flatEdgeServers = new ArrayList<>();
        int dcIndex = 0;
        for (DataCenter dc : simManager.getDataCentersManager().getEdgeDatacenterList()) {
            int serverIndex = 0;
            for (ComputingNode node : dc.nodeList) {
                flatEdgeServers.add(new FlatEdgeServer(dcIndex, serverIndex, node));
                serverIndex++;
            }
            dcIndex++;
        }
    }

    public void getDecision(Map<Integer, TaskNode> job){
        List<TaskNode> tasklist = new ArrayList<>();
        for (Map.Entry<Integer, TaskNode> task : job.entrySet()) {
            tasklist.add(task.getValue());
        }
        optimizeOffloading(tasklist);
    }

    List<OffloadingDecision> optimizeOffloading(List<TaskNode> tasklist) {
        Problem problem = new AbstractProblem(tasklist.size(), 3) {
            @Override
            public void evaluate(Solution solution) {
                List<OffloadingDecision> decisions = decodeSolution(solution, tasklist);
                double totalExecutionTime = 0;
                double totalEnergyConsumption = 0;
                
                for(OffloadingDecision decision : decisions){
                    totalExecutionTime += calculateExecutionTime(decision);
                    totalEnergyConsumption += calculateEnergyConsumption(decision);
                }
                double loadBalance = calculateLoadBalance(decisions);

                solution.setObjective(0, totalExecutionTime);
                solution.setObjective(1, totalEnergyConsumption);
                solution.setObjective(2, loadBalance);
            }

            @Override
            public Solution newSolution() {
                Solution solution = new Solution(getNumberOfVariables(), getNumberOfObjectives());
                for (int i = 0; i < getNumberOfVariables(); i++) {
                    solution.setVariable(i, EncodingUtils.newInt(0, 1 + flatEdgeServers.size()));
                }
                return solution;
            }
        };

        NondominatedPopulation result = new Executor()
                .withProblem(problem)
                .withAlgorithm("NSGAIII")
                .withMaxEvaluations(POPULATION_SIZE * MAX_GENERATIONS)
                .run();

        // Pick the solution from the Pareto front that minimizes a balanced scalarization
        Solution best = null;
        double minScore = Double.MAX_VALUE;
        for (Solution solution : result) {
            double score = 0.5 * solution.getObjective(0) + 0.3 * solution.getObjective(1) + 0.1 * solution.getObjective(2);
            if (score < minScore) {
                minScore = score;
                best = solution;
            }
        }
        
        if (best == null) {
            // Fallback if something goes wrong
            best = problem.newSolution();
        }

        return decodeSolution(best, tasklist);
    }

    private List<OffloadingDecision> decodeSolution(Solution solution, List<TaskNode> tasklist) {
        List<OffloadingDecision> decisions = new ArrayList<>();
        for (int i = 0; i < solution.getNumberOfVariables(); i++) {
            int v = EncodingUtils.getInt(solution.getVariable(i));
            if (v == 0) {
                decisions.add(new OffloadingDecision(tasklist.get(i), 0, 0, 1)); // UE
            } else if (v == 1) {
                decisions.add(new OffloadingDecision(tasklist.get(i), 0, 0, 2)); // Cloud
            } else {
                FlatEdgeServer fs = flatEdgeServers.get(v - 2);
                decisions.add(new OffloadingDecision(tasklist.get(i), fs.dcIndex, fs.serverIndex, 0)); // Edge
            }
        }
        return decisions;
    }

    private double calculateExecutionTime(OffloadingDecision offloadingDecision){
        ComputingNode computingNode = null;
        double transmission_time = 0;
        if(offloadingDecision.getDecision() == 1){
            computingNode = offloadingDecision.getTaskNode().getEdgeDevice();
        } else if(offloadingDecision.getDecision() == 0){
            computingNode = simulationManager.getDataCentersManager().getEdgeDatacenterList().get(offloadingDecision.getDcIndex()).nodeList.get(offloadingDecision.getServerIndex());
            transmission_time = Helper.calculateTransmissionLatency(offloadingDecision.getTaskNode(), computingNode);
        } else{
            computingNode = simulationManager.getDataCentersManager().getCloudDatacentersList().get(0).nodeList.get(0);
            transmission_time = Helper.calculateTransmissionLatency(offloadingDecision.getTaskNode(), computingNode);
        }

        double cpu_time = Helper.calculateExecutionTime(computingNode, offloadingDecision.getTaskNode());
        return cpu_time + transmission_time;
    }

    private double calculateLoadBalance(List<OffloadingDecision> decisions) {
        double[] util = new double[flatEdgeServers.size()];
        int edgeTaskCount = 0;
        for (OffloadingDecision decision : decisions) {
            if (decision.getDecision() == 0) { // Edge
                // Find which flat edge server this corresponds to
                int idx = 0;
                for (int i = 0; i < flatEdgeServers.size(); i++) {
                    if (flatEdgeServers.get(i).dcIndex == decision.getDcIndex() && flatEdgeServers.get(i).serverIndex == decision.getServerIndex()) {
                        idx = i;
                        break;
                    }
                }
                util[idx] += 1.0; // Increment load
                edgeTaskCount++;
            }
        }
        
        if (edgeTaskCount == 0 || flatEdgeServers.size() == 0) return 0;
        
        double averageUtilization = (double) edgeTaskCount / flatEdgeServers.size();
        double sumSquaredDifferences = 0;
        for (int i = 0; i < flatEdgeServers.size(); i++) {
            double difference = util[i] - averageUtilization;
            sumSquaredDifferences += difference * difference;
        }

        return Math.sqrt(sumSquaredDifferences / flatEdgeServers.size());
    }

    private double calculateEnergyConsumption(OffloadingDecision offloadingDecision){
        double energy = 0;
        if(offloadingDecision.getDecision() == 1){
            ComputingNode computingNode = offloadingDecision.getTaskNode().getEdgeDevice();
            energy = Helper.dynamicEnergyConsumption(offloadingDecision.getTaskNode().getLength(), computingNode.getMipsPerCore(), computingNode.getMipsPerCore());
        } else if(offloadingDecision.getDecision() == 0){
            energy = Helper.calculateRemoteEnergyConsumption(offloadingDecision.getTaskNode());
        } else{
            energy = Helper.calculateRemoteEnergyConsumption(offloadingDecision.getTaskNode());
        }

        return energy;
    }
}
