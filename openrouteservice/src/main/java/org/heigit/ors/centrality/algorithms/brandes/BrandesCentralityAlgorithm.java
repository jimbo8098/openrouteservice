package org.heigit.ors.centrality.algorithms.brandes;

import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.Graph;
import com.graphhopper.util.EdgeExplorer;
import com.graphhopper.util.EdgeIterator;
import org.heigit.ors.centrality.algorithms.CentralityAlgorithm;
import org.heigit.ors.common.Pair;

import java.util.*;

public class BrandesCentralityAlgorithm implements CentralityAlgorithm {
    protected Graph graph;
    protected Weighting weighting;
    protected EdgeExplorer explorer;

    public void init(Graph graph, Weighting weighting, EdgeExplorer explorer)
    {
        this.graph = graph;
        this.weighting = weighting;
        this.explorer = explorer;
    }

    private class QueueElement implements Comparable<QueueElement> {
        public Double dist;
        public Integer pred;
        public Integer v;

        public QueueElement(Double dist, Integer pred, Integer v) {
            this.dist = dist;
            this.pred = pred;
            this.v = v;
        }

        //TODO: x.compareTo(y)==0 doesn't imply x.equals(y), so instead of implementing compareTo,
        //      the queue that uses it should use a custom comparator.
        public int compareTo(QueueElement other) {
            return Double.compare(this.dist, other.dist);
        }
    }
    // this implementation follows the code given in
    // "A Faster Algorithm for Betweenness Centrality" by Ulrik Brandes, 2001
    public Map<Integer, Double> computeNodeCentrality(List<Integer> nodesInBBox) throws Exception {
        Map<Integer, Double> betweenness = new HashMap<>();

        // c_b[v] = 0 forall v in V
        for (int v: nodesInBBox) {
            betweenness.put(v,0.0d);
        }

        for (int s : nodesInBBox) {
            Stack<Integer> S = new Stack<>();
            Map<Integer, List<Integer>> P = new HashMap<>();
            Map<Integer, Integer> sigma = new HashMap<>();

            // single source shortest path
            // S, P, sigma = SingleSourceDijkstra(graph, nodesInBBox, s);

            for (int v : nodesInBBox) {
                P.put(v, new ArrayList<>());
                sigma.put(v, 0);
            }
            sigma.put(s, 1);

            Map<Integer, Double> D = new HashMap<>();
            Map<Integer, Double> seen = new HashMap<>();
            seen.put(s, 0.0d);

            PriorityQueue<QueueElement> Q = new PriorityQueue<>();

            Q.add(new QueueElement(0d, s, s));

            // check that everything has the length it should.
            assert S.empty(); //S should be empty
            assert seen.size() == 1;

            while (Q.peek() != null) {
                QueueElement first = Q.poll();
                Double dist = first.dist;
                Integer pred = first.pred;
                Integer v = first.v;

                if (D.containsKey(v)) {
                    continue;
                }

                sigma.put(v, sigma.get(v) + sigma.get(pred));
                S.push(v);
                D.put(v, dist);

                // iterate all edges connected to v
                EdgeIterator iter = explorer.setBaseNode(v);

                while (iter.next()) {
                    int w = iter.getAdjNode(); // this is the node this edge state is "pointing to"
                    if (!nodesInBBox.contains(w)) {
                        // Node not in bbox, skipping edge
                        continue;
                    }

                    if (D.containsKey(w)) { // This is only possible if weights are always bigger than 0, which should be given for real-world examples.
                        // Node already checked, skipping edge
                        continue;
                    }

                    Double vw_dist = dist + weighting.calcWeight(iter, false, EdgeIterator.NO_EDGE);

                    if (seen.containsKey(w) && (Math.abs(vw_dist - seen.get(w)) < 0.000001d)) {
                        sigma.put(w, sigma.get(w) + sigma.get(v));
                        List<Integer> predecessors = P.get(w);
                        predecessors.add(v);
                        P.put(w, predecessors);
                    } else if (!seen.containsKey(w) || vw_dist < seen.get(w)) {
                        seen.put(w, vw_dist);
                        Q.add(new QueueElement(vw_dist, v, w));
                        sigma.put(w, 0);
                        ArrayList<Integer> predecessors = new ArrayList<>();
                        predecessors.add(v);
                        P.put(w, predecessors);
                    }
                }
            }

            // accumulate betweenness
            Map<Integer, Double> delta = new HashMap<>();

            for (Integer v : S) {
                delta.put(v, 0.0d);
            }

            while (!S.empty()) {
                Integer w = S.pop();
                Double coefficient = (1 + delta.get(w)) / sigma.get(w);
                for (Integer v : P.get(w)) {
                    delta.merge(v, sigma.get(v) * coefficient, Double::sum);
                }
                if (w != s) {
                    betweenness.merge(w, delta.get(w), Double::sum);
                }
            }
        }

        return betweenness;
    }

    public Map<Pair<Integer, Integer>, Double> computeEdgeCentrality(List<Integer> nodesInBBox) throws Exception {
        Map<Pair<Integer, Integer>, Double> edgeBetweenness = new HashMap<>();

        //initialize betweenness for all edges
        for (int s : nodesInBBox) {
            EdgeIterator iter = explorer.setBaseNode(s);
            while (iter.next()) {
                Pair<Integer, Integer> p = new Pair<>(iter.getBaseNode(), iter.getAdjNode());
                edgeBetweenness.put(p, 0d);
            }
        }

        for (int s : nodesInBBox) {
            Stack<Integer> S = new Stack<>();
            Map<Integer, List<Integer>> P = new HashMap<>();
            Map<Integer, Integer> sigma = new HashMap<>();

            // single source shortest path
            //S, P, sigma = SingleSourceDijkstra(graph, nodesInBBox, s);

            for (int v : nodesInBBox) {
                P.put(v, new ArrayList<>());
                sigma.put(v, 0);
            }
            sigma.put(s, 1);

            Map<Integer, Double> D = new HashMap<>();
            Map<Integer, Double> seen = new HashMap<>();
            seen.put(s, 0.0d);

            PriorityQueue<QueueElement> Q = new PriorityQueue<>();

            Q.add(new QueueElement(0d, s, s));

            // check that everything has the length it should.
            assert S.empty(); //S should be empty
            assert seen.size() == 1;

            while (Q.peek() != null) {
                QueueElement first = Q.poll();
                Double dist = first.dist;
                Integer pred = first.pred;
                Integer v = first.v;

                if (D.containsKey(v)) {
                    continue;
                }

                sigma.put(v, sigma.get(v) + sigma.get(pred));
                S.push(v);
                D.put(v, dist);

                // iterate all edges connected to v
                EdgeIterator iter = explorer.setBaseNode(v);

                while (iter.next()) {
                    int w = iter.getAdjNode(); // this is the node this edge state is "pointing to"
                    if (!nodesInBBox.contains(w)) {
                        // Node not in bbox, skipping edge
                        continue;
                    }

                    if (D.containsKey(w)) { // This is only possible if weights are always bigger than 0, which should be given for real-world examples.
                        // Node already checked, skipping edge
                        continue;
                    }

                    Double vw_dist = dist + weighting.calcWeight(iter, false, EdgeIterator.NO_EDGE);

                    if (seen.containsKey(w) && (Math.abs(vw_dist - seen.get(w)) < 0.000001d)) {
                        sigma.put(w, sigma.get(w) + sigma.get(v));
                        List<Integer> predecessors = P.get(w);
                        predecessors.add(v);
                        P.put(w, predecessors);
                    } else if (!seen.containsKey(w) || vw_dist < seen.get(w)) {
                        seen.put(w, vw_dist);
                        Q.add(new QueueElement(vw_dist, v, w));
                        sigma.put(w, 0);
                        ArrayList<Integer> predecessors = new ArrayList<>();
                        predecessors.add(v);
                        P.put(w, predecessors);
                    }
                }
            }

            // accumulate betweenness
            Map<Integer, Double> delta = new HashMap<>();

            for (Integer v : S) {
                delta.put(v, 0.0d);
            }

            while (!S.empty()) {
                Integer w = S.pop();
                Double coefficient = (1 + delta.get(w)) / sigma.get(w);
                for (Integer v : P.get(w)) {
                    delta.merge(v, sigma.get(v) * coefficient, Double::sum);
                    edgeBetweenness.merge(new Pair<>(v,w), sigma.get(v) * coefficient, Double::sum);
                }
            }
        }

        return edgeBetweenness;
    }
}