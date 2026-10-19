package dev.mfa.incident.opstools.service;

import dev.mfa.incident.opstools.config.Scenario;

/** A catalogued service together with the scenario it is configured to simulate. */
record SimulatedService(ServiceProfile profile, Scenario scenario) {}
