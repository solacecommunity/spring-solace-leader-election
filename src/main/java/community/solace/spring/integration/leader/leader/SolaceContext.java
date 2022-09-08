package community.solace.spring.integration.leader.leader;

import org.springframework.integration.leader.Candidate;
import org.springframework.integration.leader.Context;

/**
 * Implementation of leadership context backed by Solace.
 */
public class SolaceContext implements Context {

    private final Candidate candidate;
    private final Runnable yield;

    private boolean isLeader;
    private boolean isJoined;

    SolaceContext(Candidate candidate, Runnable yield) {
        this.candidate = candidate;
        this.yield = yield;
        this.isJoined = false;
    }

    @Override
    public boolean isLeader() {
        return isLeader;
    }

    public synchronized void setLeader(boolean leader) {
        isLeader = leader;
    }

    public boolean isJoined() {
        return isJoined;
    }

    public synchronized void setJoined() {
        isJoined = true;
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
                "SolaceContext{role=%s, id=%s, joined=%s, isLeader=%s}",
                candidate.getRole(),
                candidate.getId(),
                isJoined(),
                isLeader()
        );
    }

    double getGaugeValue() {
        if (!isJoined()) {
            return -1;
        }

        return (isLeader()) ? 1 : 0;
    }
}