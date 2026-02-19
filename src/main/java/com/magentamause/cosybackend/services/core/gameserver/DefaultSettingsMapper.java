package com.magentamause.cosybackend.services.core.gameserver;

import com.magentamause.cosybackend.entities.gameserver.GameServerEntity;
import com.magentamause.cosybackend.entities.layout.*;
import com.magentamause.cosybackend.entities.metric.MetricType;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class DefaultSettingsMapper {
    public void createDefaultLayout(GameServerEntity gameServer) {
        gameServer.setMetricLayout(
                List.of(
                        createMetricLayout(MetricType.CPU_PERCENT),
                        createMetricLayout(MetricType.MEMORY_USAGE)));
        gameServer.setPrivateDashboardLayouts(
                List.of(
                        createPrivateDashboardLayout(
                                DashboardTypes.METRIC, MetricType.CPU_PERCENT),
                        createPrivateDashboardLayout(DashboardTypes.LOGS, null)));
        gameServer.setPublicDashboardLayouts(
                List.of(createPublicDashboardLayout())
        );
    }

    private MetricLayout createMetricLayout(MetricType metricType) {
        MetricLayout layout = new MetricLayout();
        layout.setMetricType(metricType);
        layout.setSize(Size.MEDIUM);
        return layout;
    }

    private PrivateDashboardLayout createPrivateDashboardLayout(
            DashboardTypes type, MetricType metricType) {
        PrivateDashboardLayout layout = new PrivateDashboardLayout();
        layout.setPrivateDashboardTypes(type);
        layout.setSize(Size.MEDIUM);
        if (metricType != null) {
            layout.setMetricType(metricType);
        }
        return layout;
    }

    private PublicDashboardLayout createPublicDashboardLayout() {
        PublicDashboardLayout layout = new PublicDashboardLayout();
        layout.setPublicDashboardTypes(DashboardTypes.METRIC);
        layout.setSize(Size.LARGE);
        layout.setMetricType(MetricType.CPU_PERCENT);
        return layout;
    }
}
