package com.magentamause.cosybackend.services.core.gameserver;

import com.magentamause.cosybackend.entities.gameserver.GameServerEntity;
import com.magentamause.cosybackend.entities.layout.MetricLayout;
import com.magentamause.cosybackend.entities.layout.Size;
import com.magentamause.cosybackend.entities.layout.privatedashboard.PrivateDashboardLayout;
import com.magentamause.cosybackend.entities.layout.privatedashboard.PrivateDashboardTypes;
import com.magentamause.cosybackend.entities.metric.MetricType;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class DefaultSettingsMapper {
    public void createDefaultLayout(GameServerEntity gameServer) {
        gameServer.setMetricLayout(
                List.of(
                        createMetricLayout(MetricType.CPU_PERCENT.getValue()),
                        createMetricLayout(MetricType.MEMORY_USAGE.getValue())));
        gameServer.setPrivateDashboardLayouts(
                List.of(
                        createPrivateDashboardLayout(
                                PrivateDashboardTypes.METRIC, MetricType.CPU_PERCENT),
                        createPrivateDashboardLayout(PrivateDashboardTypes.LOGS, null)));
    }

    private MetricLayout createMetricLayout(String metricType) {
        MetricLayout layout = new MetricLayout();
        layout.setMetricType(metricType);
        layout.setSize(Size.MEDIUM);
        return layout;
    }

    private PrivateDashboardLayout createPrivateDashboardLayout(
            PrivateDashboardTypes type, MetricType metricType) {
        PrivateDashboardLayout layout = new PrivateDashboardLayout();
        layout.setPrivateDashboardTypes(type);
        layout.setSize(Size.MEDIUM);
        if (metricType != null) {
            layout.setMetricType(metricType.getValue());
        }
        return layout;
    }
}
