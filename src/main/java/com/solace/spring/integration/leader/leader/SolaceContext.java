package com.solace.spring.integration.leader.leader;

import org.springframework.integration.leader.Candidate;
import org.springframework.integration.leader.Context;

/**
 * Implementation of leadership context backed by Solace.
 */
public class SolaceContext implements Context {

    private final Candidate candidate;
    private Runnable yield;

    private boolean isLeader;

    SolaceContext(Candidate candidate, Runnable yield) {
        this.candidate = candidate;
        this.yield = yield;
    }

    @Override
    public boolean isLeader() {
        return isLeader;
    }

    public synchronized void setLeader(boolean leader) {
        isLeader = leader;
    }

    @Override
    public void yield() {
        if (isLeader) {
            this.yield.run();
        }
    }

    @Override
    public String getRole() {
        return candidate.getRole();
    }

    @Override
    public String toString() {
        return String.format(
                "SolaceContext{role=%s, id=%s, isLeader=%s}",
                candidate.getRole(),
                candidate.getId(),
                isLeader());
    }

}