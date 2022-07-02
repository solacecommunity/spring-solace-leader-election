package community.solace.spring.integration.leader;

import com.solacesystems.jcsmp.impl.client.ClientInfoProvider;

class SolaceBinderClientInfoProvider extends ClientInfoProvider {
    public String getSoftwareVersion() {
        return String.format("@project.version@ (%s)", super.getSoftwareVersion());
    }

    public String getSoftwareDate() {
        return String.format("@build.timestamp@ (%s)", super.getSoftwareDate());
    }

    public String getPlatform() {
        return this.getPlatform("solace-spring-integration-leader");
    }
}
