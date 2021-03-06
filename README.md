# GIS Cup 2019: Trip Bandit Agent

This project provides the source code for the submission "Optimizing the Spatio-Temporal Resource Search Problem with Reinforcement Learning". It is based on the COMSET simulator described in the <a href="https://sigspatial2019.sigspatial.org/giscup2019/problem"> 2019 GISCUP Problem Definition</a>. COMSET simulates crowdsourced taxicabs (called <i>agents</i>) searching for customers (called <i>resources</i>) to pick up in a city. The simulator served as a standard testbed for the CUP contestants.

## Compiling and running the code:
cd into the project directory  
mvn compile exec:java -Dexec.mainClass="Main"

## Train the agent:
cd into the project directory  
mvn compile exec:java -Dexec.mainClass="UserExamples.TripsBanditAgent" -Dexec.args='ExperimentName dataset.csv learning_rate exponential_lr_decay'

## Description:
The implemented approach relies on round trips. The basic idea is that every agent gets assigned to a round trip, drives to and subsequently on the round trip until he gets assigned to a resource. If an agent finishes the trip before getting assigned to a resource, he is assigned to a new round trip. We compute one round trip with a radius of 5 minutes travel time for each intersection. The intuition is that once an agent is on a trip, he can reach each resource that appears on or within the trip before the resource expires. After having all trips, we minimize them by throwing away some of the trips, i.e., those trips whose reachable nodes are entirely covered by other trips. For the assignment step, we learn a probability vector with each dimension corresponding to one of the remaining trips. The probability vector corresponds to the normalized vector that we initially get from the absolute number of pickups that happened within the trip's isochrone during the training phase. We refine this vector by a multi-armed bandit (reinforcement learning) like approach. We use the Agent "TripsBanditAgent" in the training phase, where each individual agent chooses one trip according to the probability distribution defined above. The agent tracks how long it stays on the chosen trip and uses the time as costs (negative reward). We update the parameters of the softmax policy by a stochastic gradient descent approach where the objective is similar to the REINFORCE trick in reinforcement learning. After the training phase the improved probability distribution will be applied at the "TripsAgent".

