import unittest
from vpn_diagnostics import has_vpn_agent

class VpnDiagnosticsTests(unittest.TestCase):
    def test_actual_vpn(self):
        self.assertTrue(has_vpn_agent('  NetworkAgentInfo{network{104} ni{VPN CONNECTED} nc{[ Transports: VPN Capabilities: INTERNET]}'))
    def test_vpn_with_underlying_transport(self):
        for transports in ('WIFI|VPN', 'CELLULAR|VPN', 'VPN|WIFI'):
            self.assertTrue(has_vpn_agent('  NetworkAgentInfo{network{104} nc{[ Transports: '+transports+' Capabilities: INTERNET]}'))
    def test_listener_is_not_an_agent(self):
        self.assertFalse(has_vpn_agent('uid/pid:10181/771 callbackRequest: 53 [NetworkRequest [ LISTEN id=53, [ Transports: VPN RequestorPkg: com.android.systemui ] ]]'))
    def test_physical_agent_not_vpn_capability(self):
        self.assertFalse(has_vpn_agent('  NetworkAgentInfo{network{100} nc{[ Transports: WIFI Capabilities: INTERNET&NOT_VPN]}'))
