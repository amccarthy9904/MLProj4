import java.util.ArrayList;

/**
 * Competitive Neural Network using back-propagation for clustering
 */
public class BackpropNN extends Clustering{

    /**
     * NUmber of outputs = number of clusters
     */
    private int numOutputs;

    /**
     * the number of inputs
     */
    private int numInputs;

    /**
     * number of hidden layers, default is 1
     */
    private final int numHiddenLayers = 1;

    /**
     * number of hidden nodes in each hidden layer, default is 20
     */
    private int[] numHiddenNodesPerLayer = {Driver.numHiddenNodes};

    /**
     * the learning rate
     */
    private double learningRate = Driver.learningRate;

    /**
     * the momentum
     */
    private double momentum = Driver.momentum;

    /**
     * holds the computed derivatives fro hidden layer
     */
    private Double[] hiddenDerivatives;

    /**
     * holds the computed derivatives for output layer
     */
    private Double[] outputDerivatives;

    @Override
    /**
     * A neural network using backpropagation is used to assign points to clusters
     */
    public int[] cluster(Double[][] data, int numClusters){
        // the number of outputs
        this.numOutputs = numClusters;

        // the number of inputs
        this.numInputs = data[0].length;

        // the average of the inputs to each node, used to update weights
        ArrayList<Double> avgInput = new ArrayList<>();

        // the total number of weights
        int numWeights = this.calculateNumWeights(this.numInputs);

        // the best clustering found so far and associated error
        int[] bestClustering = new int[numClusters];
        double bestError = Double.MAX_VALUE;

        // the current weights during each iteration
        ArrayList<Double> weights = new ArrayList<>();

        // the weight change and summed weight changes over each iteration, initialize both to 0
        // sumWeightChanges is used to calculate the average weightChange over any number of iterations
        ArrayList<Double> prevWeightChange = new ArrayList<>();
        ArrayList<Double> sumWeightChanges = new ArrayList<>();
        for(int weightIter = 0; weightIter < numWeights; weightIter++){
            prevWeightChange.add(0.0);
            sumWeightChanges.add(0.0);
        }

        // initialize network and start loop
        this.initializeNetwork(weights, numWeights);
        boolean hasConverged = false;
        int loopIter = 0;
        int iterSinceBestUpdate = 0;
        do{
            // Cluster each data point
            int[] clustering = new int[data.length];
            this.hiddenDerivatives = new Double[this.numHiddenNodesPerLayer[0]];
            this.outputDerivatives = new Double[this.numOutputs];
            avgInput.clear();
            for(int iter = 0; iter < this.numHiddenNodesPerLayer[0] + this.numOutputs; iter++){
                avgInput.add(0.0);
            }
            for(int pointIter = 0; pointIter < data.length; pointIter++){
                clustering[pointIter] = this.sendThroughNetwork(data[pointIter], weights, avgInput);
            }

            // Determine error and backpropogate
            double error = this.calculateError(data, clustering);
            this.backpropogate(error, weights, prevWeightChange, avgInput);

            // Update bestClustering
            if(error < bestError){
                iterSinceBestUpdate = 0;
                bestError = error;
                bestClustering = clustering;
            }

            // Update sumWeightChanges
            for(int weightIter = 0; weightIter < sumWeightChanges.size(); weightIter++){
                double temp = sumWeightChanges.get(weightIter);
                sumWeightChanges.remove(weightIter);
                sumWeightChanges.add(weightIter, temp + prevWeightChange.get(weightIter));
            }

            // Calculate convergence every 100 iterations
            if((loopIter + 1) % 100 == 0){
                // average sumWeightChanges
                ArrayList<Double> avgWeightChanges = new ArrayList<>();
                for(int weightIter = sumWeightChanges.size() - 1; weightIter >= 0; weightIter--){
                    avgWeightChanges.add(sumWeightChanges.get(weightIter) / (loopIter + 1));
                }
                hasConverged = this.hasConverged(avgWeightChanges, iterSinceBestUpdate);
                iterSinceBestUpdate++;

                // print current best error
                System.out.println("Current best error: " + bestError);
            }
            loopIter++;
        }
        while(loopIter < 100000 && !hasConverged);
        return bestClustering;
    }

    /**
     * Initializes the weights of the network
     */
    private void initializeNetwork(ArrayList<Double> weights, int numWeights){
        // fix unknown bug
        if(this.numOutputs > this.numHiddenNodesPerLayer[0]){
            this.numHiddenNodesPerLayer = new int[this.numOutputs];
        }

        // then randomly initialize weights to (-0.5, 0.5), initialize prev weight change to zero
        for(int weightIter = 0; weightIter < numWeights; weightIter++){
            if(Math.random() < 0.5){
                weights.add(Math.random() * 0.5);
            }
            else{
                weights.add(Math.random() * -0.5);
            }
        }
    }

    /**
     * Calculates the number of weights needed
     * @param numInputs the number of inputs used
     * @return the number of weights needed
     */
    private int calculateNumWeights(int numInputs){
        int numWeights = 0;
        for(int layerIter = 0; layerIter < this.numHiddenLayers + 1; layerIter++){
            if(layerIter == 0){
                numWeights += (numInputs * this.numHiddenNodesPerLayer[layerIter]);
            }
            else if(layerIter == this.numHiddenLayers){
                numWeights += (this.numHiddenNodesPerLayer[layerIter - 1] * this.numOutputs);
            }
            else{
                numWeights += (this.numHiddenNodesPerLayer[layerIter - 1] * this.numHiddenNodesPerLayer[layerIter]);
            }
        }
        return numWeights;
    }

    /**
     * Sends a given data point through the network to be clustered and returns the output
     * @param dataPoint the data point to cluster
     * @return an int representing the cluster chosen for the given data point
     */
    private int sendThroughNetwork(Double[] dataPoint, ArrayList<Double> weights, ArrayList<Double> avgInputs){
        // used as the values passed between the layers
        ArrayList<Double> currentLayer = new ArrayList<>();

        int avgInputIter = 0;

        // send values through each layer of the network, layerIter is really a between layer iter
        for(int layerIter = 0; layerIter < this.numHiddenLayers + 1; layerIter++){
            // from input layer to first hidden layer
            if(layerIter == 0){
                int numWeights = dataPoint.length * this.numHiddenNodesPerLayer[0];
                int inputIter = 0;
                for(int weightIter = 0; weightIter < numWeights; weightIter++){
                    currentLayer.add(dataPoint[inputIter] * weights.get(weightIter));
                    if((weightIter + 1) % this.numHiddenNodesPerLayer[0] == 0){
                        inputIter++;
                    }
                }

                // sum values in currentLayer as input for the first hidden layer and compute sigmoid
                int nodeIter = 0;
                int iter = 0;
                for(int valueIter = 0; valueIter < currentLayer.size(); valueIter++){
                    if(iter % this.numHiddenNodesPerLayer[0] == 0){
                        nodeIter = 0;
                    }
                    if(iter >= this.numHiddenNodesPerLayer[0]){
                        double sum = currentLayer.get(valueIter) + currentLayer.get(nodeIter);
                        currentLayer.remove(valueIter);
                        valueIter--;
                        currentLayer.remove(nodeIter);
                        currentLayer.add(nodeIter, sum);
                    }
                    nodeIter++;
                    iter++;
                }
                for(int valueIter = 0; valueIter < currentLayer.size(); valueIter++){
                    double temp = avgInputs.get(avgInputIter) + currentLayer.get(valueIter);
                    avgInputs.remove(avgInputIter);
                    avgInputs.add(avgInputIter, temp);
                    avgInputIter++;
                    double sigmoid = this.sigmoid(currentLayer.get(valueIter));
                    currentLayer.remove(valueIter);
                    currentLayer.add(valueIter, sigmoid);
                    this.hiddenDerivatives[valueIter] = this.sigmoidDerivative(sigmoid);
                }
            }
            // from last hidden layer to output layer
            else if(layerIter == this.numHiddenLayers){
                int numWeights = this.numHiddenNodesPerLayer[this.numHiddenLayers - 1] * this.numOutputs;
                int endingWeight = weights.size();
                int startingWeight = endingWeight - numWeights;
                int inputIter = 0;
                double[] inputs = new double[currentLayer.size()];
                for(int iter = 0; iter < inputs.length; iter++){
                    inputs[iter] = currentLayer.get(iter);
                }
                currentLayer.clear();
                for(int weightIter = startingWeight; weightIter < endingWeight; weightIter++){
                    currentLayer.add(inputs[inputIter] * weights.get(weightIter));
                    if((weightIter + 1) % this.numHiddenNodesPerLayer[this.numHiddenLayers - 1] == 0){
                        inputIter++;
                    }
                }

                // sum values in currentLayer as input for the first hidden layer and compute sigmoid
                int nodeIter = 0;
                int iter = 0;
                for(int valueIter = 0; valueIter < currentLayer.size(); valueIter++){
                    if(iter % this.numOutputs == 0){
                        nodeIter = 0;
                    }
                    if(iter >= this.numOutputs) {
                        double temp = currentLayer.get(valueIter) + currentLayer.get(nodeIter);
                        currentLayer.remove(valueIter);
                        valueIter--;
                        currentLayer.remove(nodeIter);
                        currentLayer.add(nodeIter, temp);
                    }
                    nodeIter++;
                    iter++;
                }
                for(int valueIter = 0; valueIter < currentLayer.size(); valueIter++){
                    double temp = avgInputs.get(avgInputIter) + currentLayer.get(valueIter);
                    avgInputs.remove(avgInputIter);
                    avgInputs.add(avgInputIter, temp);
                    avgInputIter++;
                    double sigmoid = this.sigmoid(currentLayer.get(valueIter));
                    currentLayer.remove(valueIter);
                    currentLayer.add(valueIter, sigmoid);
                    this.outputDerivatives[valueIter] = this.sigmoidDerivative(sigmoid);
                }
            }

            // this got really complicated really fast, not needed with only 1 hidden layer, so Im sticking to 1 hidden layer
            // from hidden layer to next hidden layer
//            else{
//                int numWeights = this.numHiddenNodesPerLayer[layerIter - 1] * this.numHiddenNodesPerLayer[layerIter];
//                int endingWeight = dataPoint.length * this.numHiddenNodesPerLayer[0];
//                int startingWeight = endingWeight - numWeights;
//                int inputIter = 0;
//                double[] inputs = new double[currentLayer.size()];
//                for(int iter = 0; iter < inputs.length; iter++){
//                    inputs[iter] = currentLayer.get(iter);
//                }
//                currentLayer.clear();
//                for(int weightIter = startingWeight; weightIter < endingWeight; weightIter++){
//                    currentLayer.add(inputs[inputIter] * weights.get(weightIter));
//                    if((weightIter + 1) % this.numHiddenNodesPerLayer[this.numHiddenLayers - 1] == 0){
//                        inputIter++;
//                    }
//                }
//            }
        }

        // average inputs to each node
        for(int inputIter = 0; inputIter < avgInputs.size(); inputIter++){
            double temp = avgInputs.get(inputIter);
            temp /= (this.numHiddenNodesPerLayer[0] + numOutputs);
            avgInputs.remove(inputIter);
            avgInputs.add(inputIter, temp);
        }

        // Calculate chosen cluster based on highest output node
        int maxIndex = 0;
        for(int iter = 1; iter < currentLayer.size(); iter++){
            if(currentLayer.get(iter) > currentLayer.get(maxIndex)){
                maxIndex = iter;
            }
        }
        return maxIndex;
    }

    /**
     * Computes the sigmoid function
     * @param input the input
     * @return the output
     */
    private double sigmoid(double input){
        return 1 / (1 + Math.exp(-1 * input));
    }

    /**
     * Computes the sigmoid function
     * @param input the input (the output of the sigmoid function of the same input)
     * @return the output
     */
    private double sigmoidDerivative(double input){
        return input * (1 - input);
    }

    /**
     * Calculates the error of the clustering
     * @param clustering the clustering of the data
     * @return the fitness of the clustering
     */
    private double calculateError(Double[][] data, int[] clustering){
        return Driver.evaluateClusters(data, clustering, this.numOutputs);
    }

    /**
     * backpropogates the error through the network and updates weights
     * @param error the error calculated for the current iteration
     * @param weights the weights of the current iteration
     * @param prevWeightChange the prev weight change from last iteration
     */
    private void backpropogate(double error, ArrayList<Double> weights, ArrayList<Double> prevWeightChange, ArrayList<Double> inputs){
        ArrayList<Double> newWeightChange = new ArrayList<>();
        double[] deltas = this.calculateDeltas(error, weights, this.numInputs);

        // update each weight
        for(int weightIter = 0; weightIter < weights.size(); weightIter++){
            double weightChange = 0.0;

            // first layer of weights
            if(weightIter < this.numInputs * this.numHiddenNodesPerLayer[0]){
                int deltaIndex = this.numInputs + (weightIter % (this.numHiddenNodesPerLayer[0] - 1));
                int inputIndex = weightIter % (this.numHiddenNodesPerLayer[0] - 1);
                weightChange += ((1 - this.momentum) * this.learningRate * deltas[deltaIndex] * inputs.get(inputIndex));
                weightChange += (this.momentum * prevWeightChange.get(weightIter));
                double originalWeight = weights.get(weightIter);
                weights.remove(weightIter);
                weights.add(weightIter, originalWeight + weightChange);
                prevWeightChange.remove(weightIter);
                prevWeightChange.add(weightIter, weightChange);
            }

            // second/last layer of weights
            else{
                int deltaIndex = this.numInputs + this.numHiddenNodesPerLayer[0] + (weightIter % (this.numOutputs - 1));
                int inputIndex = this.numHiddenNodesPerLayer[0] + (weightIter % (this.numOutputs - 1));
                weightChange += ((1 - this.momentum) * this.learningRate * deltas[deltaIndex] * inputs.get(inputIndex));
                weightChange += (this.momentum * prevWeightChange.get(weightIter));
                double originalWeight = weights.get(weightIter);
                weights.remove(weightIter);
                weights.add(weightIter, originalWeight + weightChange);
                prevWeightChange.remove(weightIter);
                prevWeightChange.add(weightIter, weightChange);
            }
        }
    }

    /**
     * Calculates the delta values for each node in the network
     * @param error the fitness of the clustering
     * @param weights the weights of the network
     * @return delta values for each node in the network
     */
    private double[] calculateDeltas(double error, ArrayList<Double> weights, int numInputs){
        // initialize delta array
        int numNodes = numInputs;
        for(int nodeIter = 0; nodeIter < this.numHiddenLayers; nodeIter++){
            numNodes += this.numHiddenNodesPerLayer[nodeIter];
        }
        numNodes += this.numOutputs;
        double[] deltas = new double[numNodes];

        // calculate output node deltas
        for(int outIter = numNodes - this.numOutputs; outIter < numNodes; outIter++){
            deltas[outIter] = -1 * error * this.outputDerivatives[outIter - numNodes + this.numOutputs];
        }

        // calculate hidden node deltas
        for(int layerIter = this.numHiddenLayers - 1; layerIter >= 0; layerIter--){
            int startIndex = numNodes - this.numOutputs - this.numHiddenNodesPerLayer[layerIter];
            int endIndex = layerIter == this.numHiddenLayers - 1 ?
                    numNodes - this.numOutputs : numNodes - this.numOutputs - this.numHiddenNodesPerLayer[layerIter + 1];
            for(int hiddenIter = startIndex; hiddenIter < endIndex; hiddenIter++){
                double downstreamSum = 0.0;
                if(layerIter == this.numHiddenLayers - 1){
                    int multiple = (hiddenIter - startIndex) * this.numOutputs;
                    int weightIndex = weights.size() - 1 - (this.numHiddenNodesPerLayer[layerIter] * this.numOutputs) + multiple;
                    int outputIter = deltas.length;
                    while(outputIter >= deltas.length - this.numOutputs){
                        outputIter--;
                        downstreamSum += (deltas[outputIter] * weights.get(weightIndex));
                        weightIndex++;
                    }
                }
                else{
                    // only needed if we have more than one hidden layer, not working yet
//                    int hiddenLayerIter = 0;
//                    int multiple = (hiddenIter - startIndex) * this.numHiddenNodesPerLayer[layerIter + 1];
//                    int weightIndex = (weights.size() - 1) - (this.numHiddenNodesPerLayer[this.numHiddenLayers - 1] * this.numOutputs) + multiple;
//                    for(int downLayerIter = this.numHiddenLayers; downLayerIter > layerIter; downLayerIter--){
//                        weightIndex -= (this.numHiddenNodesPerLayer[downLayerIter] * this.numOutputs);
//                    }
//                    while(hiddenLayerIter < this.numOutputs){
//                        hiddenLayerIter++;
//                        downstreamSum += (deltas[hiddenLayerIter] * weights.get(weightIndex));
//                        weightIndex++;
//                    }
                }
                int derivativeIndex = this.hiddenDerivatives.length;
                for(int hiddenLayerIter = this.numHiddenLayers - 1; hiddenLayerIter >= layerIter; hiddenLayerIter--){
                    derivativeIndex -= this.numHiddenNodesPerLayer[hiddenLayerIter];
                }
                derivativeIndex += (hiddenIter - startIndex);
                deltas[hiddenIter] = downstreamSum * this.hiddenDerivatives[derivativeIndex];
            }
        }

        // the first X entries will be zero, where X is the number of inputs
        return deltas;
    }

    /**
     * Returns true if the network has converged
     * @param avgWeightChange the average weight changes over a past set of iterations
     * @return true if the network has converged, false otherwise
     */
    private boolean hasConverged(ArrayList<Double> avgWeightChange, int timeSinceBest){
        if(timeSinceBest > 10) return true;
        for(int iter = 0; iter < avgWeightChange.size(); iter++){
            if(Math.abs(avgWeightChange.get(iter)) > 0.001) return false;
        }
        return true;
    }

    /**
     * Sets the number of hidden nodes per layer
     * @param numHiddenNodes the number of hidden nodes per layer
     */
    public void setNumHiddenNodesPerLayer(int numHiddenNodes){
        this.numHiddenNodesPerLayer = new int[numHiddenNodes];
    }

    /**
     * Sets the learning rate for the network
     * @param learningRate the learning rate for the network
     */
    public void setLearningRate(double learningRate) {
        this.learningRate = learningRate;
    }

    /**
     * Sets the momentum of the network
     * @param momentum the momentum of the network
     */
    public void setMomentum(double momentum){
        this.momentum = momentum;
    }
}
