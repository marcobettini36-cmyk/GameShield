"""Parse actual NetworkAgents, not VPN listener requests, in Android dumpsys."""
import re

def has_vpn_agent(dump):
    for line in dump.splitlines():
        if 'NetworkAgentInfo{network{' not in line:
            continue
        transports = re.search(r'\bTransports:\s+([A-Z_]+(?:\|[A-Z_]+)*)', line)
        if transports and 'VPN' in transports.group(1).split('|'):
            return True
    return False
