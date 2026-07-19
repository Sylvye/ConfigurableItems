package com.bountysmp.configurableitems.action;

import java.util.List;

public sealed interface ActionStep permits ActionStep.Simple, ActionStep.Loop, ActionStep.RandomBlock, ActionStep.ForBlock {
    record Simple(String line) implements ActionStep {
    }

    record Loop(int times, int delayTicks, List<ActionStep> body) implements ActionStep {
    }

    record RandomBlock(int selectionCount, List<ActionStep> body) implements ActionStep {
    }

    record ForBlock(List<String> values, String variable, List<ActionStep> body) implements ActionStep {
    }
}
