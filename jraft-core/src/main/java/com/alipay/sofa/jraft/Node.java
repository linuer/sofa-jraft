/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alipay.sofa.jraft;

import java.util.List;

import com.alipay.sofa.jraft.closure.ReadIndexClosure;
import com.alipay.sofa.jraft.conf.Configuration;
import com.alipay.sofa.jraft.core.NodeMetrics;
import com.alipay.sofa.jraft.entity.NodeId;
import com.alipay.sofa.jraft.entity.PeerId;
import com.alipay.sofa.jraft.entity.Task;
import com.alipay.sofa.jraft.entity.UserLog;
import com.alipay.sofa.jraft.error.LogIndexOutOfBoundsException;
import com.alipay.sofa.jraft.error.LogNotFoundException;
import com.alipay.sofa.jraft.option.NodeOptions;
import com.alipay.sofa.jraft.option.RaftOptions;

/**
 * A raft replica node.
 *
 * @author boyan (boyan@alibaba-inc.com)
 *
 * 2018-Apr-03 4:06:55 PM
 */
public interface Node extends Lifecycle<NodeOptions> {

    /**
     * Get the leader peer id for redirect, null if absent.
     */
    PeerId getLeaderId();

    /**
     * Get current node id.
     */
    NodeId getNodeId();

    /**
     * Get the node metrics, only valid when node option {@link NodeOptions#isEnableMetrics()} is true.
     */
    NodeMetrics getNodeMetrics();

    /**
     * Get the raft group id.
     */
    String getGroupId();

    /**
     * Get the node options.
     */
    NodeOptions getOptions();

    /**
     * Get the raft options
     */
    RaftOptions getRaftOptions();

    /**
     * Returns true when the node is leader.
     */
    boolean isLeader();

    /**
     * Shutdown local replica node.
     *
     * @param done callback
     */
    void shutdown(Closure done);

    /**
     * Block the thread until the node is successfully stopped.
     *
     * @throws InterruptedException if the current thread is interrupted
     *         while waiting
     */
    void join() throws InterruptedException;

    /**
     * [Thread-safe and wait-free]
     *
     * Apply task to the replicated-state-machine
     *
     * About the ownership:
     * |task.data|: for the performance consideration, we will take away the
     *               content. If you want keep the content, copy it before call
     *               this function
     * |task.done|: If the data is successfully committed to the raft group. We
     *              will pass the ownership to #{@link StateMachine#onApply(Iterator)}.
     *              Otherwise we will specify the error and call it.
     *
     * @param task task to apply
     */
    void apply(Task task);

    /**
     * [Thread-safe and wait-free]
     *
     * Starts a linearizable read-only query request with request context(optional,
     * such as request id etc.) and closure.  The closure will be called when the
     * request is completed, and user can read data from state machine if the result
     * status is OK.
     *
     * @since 0.0.3
     * @param requestContext    the context of request
     * @param done              callback
     */
    void readIndex(byte[] requestContext, ReadIndexClosure done);

    /**
     * List peers of this raft group, only leader returns.
     *
     * [NOTE] <strong>when list_peers concurrency with {@link #addPeer(PeerId, Closure)}/{@link #removePeer(PeerId, Closure)},
     * maybe return peers is staled.  Because {@link #addPeer(PeerId, Closure)}/{@link #removePeer(PeerId, Closure)}
     * immediately modify configuration in memory</strong>
     *
     * @return the peer list
     */
    List<PeerId> listPeers();

    /**
     * Add a new peer to the raft group. done.run() would be invoked after this
     * operation finishes, describing the detailed result.
     *
     * @param peer peer to add
     * @param done callback
     */
    void addPeer(PeerId peer, Closure done);

    /**
     * Remove the peer from the raft group. done.run() would be invoked after
     * operation finishes, describing the detailed result.
     *
     * @param peer peer to remove
     * @param done callback
     */
    void removePeer(PeerId peer, Closure done);

    /**
     * Change the configuration of the raft group to |newPeers| , done.un()
     * would be invoked after this operation finishes, describing the detailed result.
     *
     * @param newPeers  new peers to change
     * @param done      callback
     */
    void changePeers(Configuration newPeers, Closure done);

    /**
     * Reset the configuration of this node individually, without any replication
     * to other peers before this node becomes the leader. This function is
     * supposed to be invoked when the majority of the replication group are
     * dead and you'd like to revive the service in the consideration of
     * availability.
     * Notice that neither consistency nor consensus are guaranteed in this
     * case, BE CAREFULE when dealing with this method.
     */
    Status resetPeers(Configuration newPeers);

    /**
     * Start a snapshot immediately if possible. done.run() would be invoked when
     * the snapshot finishes, describing the detailed result.
     *
     * @param done callback
     */
    void snapshot(Closure done);

    /**
     * Reset the election_timeout for the every node.
     *
     * @param electionTimeoutMs the timeout millis of election
     */
    void resetElectionTimeoutMs(int electionTimeoutMs);

    /**
     * Try transferring leadership to |peer|. If peer is ANY_PEER, a proper follower
     * will be chosen as the leader for the next term.
     * Returns 0 on success, -1 otherwise.
     *
     * @param peer the target peer of new leader
     * @return operation status
     */
    Status transferLeadershipTo(PeerId peer);

    /**
     * Read the first committed user log from the given index.
     *   Return OK on success and user_log is assigned with the very data. Be awared
     *   that the user_log may be not the exact log at the given index, but the
     *   first available user log from the given index to lastCommittedIndex.
     *   Otherwise, appropriate errors are returned:
     *        - return ELOGDELETED when the log has been deleted;
     *        - return ENOMOREUSERLOG when we can't get a user log even reaching lastCommittedIndex.
     * [NOTE] in consideration of safety, we use lastAppliedIndex instead of lastCommittedIndex
     * in code implementation.
     *
     * @param index log index
     * @return user log entry
     * @throws LogNotFoundException  the user log is deleted at index.
     * @throws LogIndexOutOfBoundsException  the special index is out of bounds.
     */
    UserLog readCommittedUserLog(long index);
}
