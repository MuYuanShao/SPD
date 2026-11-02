param(
    [Parameter(Mandatory=$true)][string]$Address,
    [Parameter(Mandatory=$true)][string]$Subnet
)
$ErrorActionPreference='Stop'
$parsed=[System.Net.IPAddress]::Parse($Address)
if($parsed.AddressFamily -ne [System.Net.Sockets.AddressFamily]::InterNetwork -or $Address -eq '127.0.0.1') { throw 'An IPv4 LAN address is required.' }
if($Subnet -notmatch '^\d{1,3}(\.\d{1,3}){3}/\d{1,2}$') { throw 'An IPv4 subnet is required.' }
$ruleName='SPD-PR-LAN-Web-1820'
$parameters=@{DisplayName='SPD-PR LAN Web 1820';Direction='Inbound';Action='Allow';Enabled='True';Profile='Any';Protocol='TCP';LocalPort=1820;LocalAddress=$Address;RemoteAddress=$Subnet}
if(Get-NetFirewallRule -Name $ruleName -ErrorAction SilentlyContinue) {
    Set-NetFirewallRule -Name $ruleName @parameters | Out-Null
} else {
    New-NetFirewallRule -Name $ruleName @parameters | Out-Null
}
Write-Output "Firewall configured: $Address`:1820 from $Subnet"
