package com.keyStone.Playroom021.service;

/** Outcome of one scheduled SLA check. */
public record SlaCheckResult(int checked, int newlyAtRisk, int newlyBreached, int statusChanges) {
}
