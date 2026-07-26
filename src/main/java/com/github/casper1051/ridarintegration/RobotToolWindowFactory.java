package com.github.casper1051.ridarintegration;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import org.jetbrains.annotations.NotNull;

public class RobotToolWindowFactory implements ToolWindowFactory {
    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        RobotDashboard dashboard = new RobotDashboard();
        Content content = ContentFactory.getInstance().createContent(dashboard, "Telemetry", false);
        toolWindow.getContentManager().addContent(content);
    }
}