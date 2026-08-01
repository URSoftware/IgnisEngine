package com.rimurusurvivors.domain;

/** Comandos tipados aceitos pelo estado de Tempest. */
public sealed interface TownCommand
        permits TownCommand.ApplyReturnReport,
                TownCommand.BeginExpedition,
                TownCommand.CompletePrioritizedProject,
                TownCommand.PrioritizeProject,
                TownCommand.ResolveExpedition,
                TownCommand.ScheduleExpedition,
                TownCommand.StartPrioritizedProject {

    record ApplyReturnReport(ReturnReport report) implements TownCommand {
        public ApplyReturnReport {
            if (report == null) {
                throw new IllegalArgumentException("Return report is required.");
            }
        }
    }

    record PrioritizeProject(String projectId) implements TownCommand {
        public PrioritizeProject {
            requireId(projectId, "Town project");
        }
    }

    record StartPrioritizedProject() implements TownCommand {
    }

    record CompletePrioritizedProject() implements TownCommand {
    }

    record ScheduleExpedition(DelegatedExpedition expedition) implements TownCommand {
        public ScheduleExpedition {
            if (expedition == null) {
                throw new IllegalArgumentException("Delegated expedition is required.");
            }
        }
    }

    record BeginExpedition(String expeditionId, boolean automatic) implements TownCommand {
        public BeginExpedition {
            requireId(expeditionId, "Expedition");
        }
    }

    record ResolveExpedition(String expeditionId, String milestoneId)
            implements TownCommand {
        public ResolveExpedition {
            requireId(expeditionId, "Expedition");
            requireId(milestoneId, "Expedition milestone");
        }
    }

    private static void requireId(String id, String label) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException(label + " id is required.");
        }
    }
}
