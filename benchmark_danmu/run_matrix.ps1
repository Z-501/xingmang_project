param(
    [Parameter(Mandatory = $true)]
    [string]$AccessToken,

    [Parameter(Mandatory = $true)]
    [long]$VideoId,

    [string]$BaseUrl = "http://127.0.0.1:8082",
    [string]$WsUrl = "ws://127.0.0.1:8082/ws/danmu"
)

$matrix = @(
    @{ Mode = "async"; Connections = 100 },
    @{ Mode = "sync"; Connections = 100 },
    @{ Mode = "sync"; Connections = 300 },
    @{ Mode = "async"; Connections = 300 },
    @{ Mode = "async"; Connections = 500 },
    @{ Mode = "sync"; Connections = 500 }
)

foreach ($item in $matrix) {
    python .\danmu_benchmark.py `
        --base-url $BaseUrl `
        --ws-url $WsUrl `
        --access-token $AccessToken `
        --video-id $VideoId `
        --mode $item.Mode `
        --connections $item.Connections `
        --messages 100 `
        --warmup-messages 10 `
        --interval-ms 20
}
