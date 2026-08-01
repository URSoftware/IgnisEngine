package com.rimurusurvivors.domain;

/** Comandos tipados aceitos pelo estado de campanha. */
public sealed interface CampaignCommand
        permits CampaignCommand.AcceptQuest,
                CampaignCommand.AdvanceObjective,
                CampaignCommand.ChooseDialogue,
                CampaignCommand.ReachMilestone {

    record AcceptQuest(String questId) implements CampaignCommand {
        public AcceptQuest {
            requireId(questId, "Quest");
        }
    }

    record AdvanceObjective(String questId, String objectiveId, int amount)
            implements CampaignCommand {
        public AdvanceObjective {
            requireId(questId, "Quest");
            requireId(objectiveId, "Objective");
            if (amount < 1) {
                throw new IllegalArgumentException("Objective progress amount must be positive.");
            }
        }
    }

    record ChooseDialogue(CampaignChoice choice) implements CampaignCommand {
        public ChooseDialogue {
            if (choice == null) {
                throw new IllegalArgumentException("Campaign dialogue choice is required.");
            }
        }
    }

    record ReachMilestone(String milestoneId) implements CampaignCommand {
        public ReachMilestone {
            requireId(milestoneId, "Milestone");
        }
    }

    private static void requireId(String id, String label) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException(label + " id is required.");
        }
    }
}
