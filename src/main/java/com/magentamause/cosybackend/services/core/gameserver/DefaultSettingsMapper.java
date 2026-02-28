package com.magentamause.cosybackend.services.core.gameserver;

import com.magentamause.cosybackend.entities.PublicDashboard;
import com.magentamause.cosybackend.entities.gameserver.GameServerEntity;
import com.magentamause.cosybackend.entities.layout.*;
import com.magentamause.cosybackend.entities.metric.MetricType;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class DefaultSettingsMapper {
    public void createDefaultLayouts(GameServerEntity gameServer) {
        gameServer.setMetricLayout(
                List.of(
                        createMetricLayout(MetricType.CPU_PERCENT),
                        createMetricLayout(MetricType.MEMORY_USAGE),
                        createMetricLayout(MetricType.NETWORK_INPUT),
                        createMetricLayout(MetricType.NETWORK_OUTPUT)));
        gameServer.setPrivateDashboardLayouts(
                List.of(
                        createPrivateDashboardLayout(
                                DashboardTypes.METRIC, Size.MEDIUM, MetricType.CPU_PERCENT),
                        createPrivateDashboardLayout(
                                DashboardTypes.METRIC, Size.MEDIUM, MetricType.MEMORY_USAGE),
                        createPrivateDashboardLayout(DashboardTypes.LOGS, Size.LARGE, null)));
        gameServer.setPublicDashboard(
                PublicDashboard.builder().enabled(false).layouts(new ArrayList<>()).build());
    }

    private MetricLayout createMetricLayout(MetricType metricType) {
        return MetricLayout.builder().metricType(metricType.getValue()).size(Size.MEDIUM).build();
    }

    private PrivateDashboardLayout createPrivateDashboardLayout(
            DashboardTypes type, Size size, MetricType metricType) {
        return PrivateDashboardLayout.builder()
                .layoutType(type)
                .size(size)
                .metricType(metricType != null ? metricType.getValue() : null)
                .build();
    }
}
