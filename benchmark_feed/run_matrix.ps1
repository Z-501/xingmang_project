param(
  [Parameter(Mandatory=$true)][string]$AccessToken,
  [Parameter(Mandatory=$true)][long]$AuthorId,
  [string]$MySqlPassword = "",
  [int[]]$FollowerCounts = @(1000, 5000, 10000, 20000),
  [int]$Rounds = 20,
  [int]$PollIntervalMs = 100
)

$ErrorActionPreference = "Stop"

foreach ($n in $FollowerCounts) {
  Write-Host ""
  Write-Host "===== Feed benchmark: $n followers =====" -ForegroundColor Cyan

  python .\feed_benchmark.py `
    --access-token "$AccessToken" `
    --author-id $AuthorId `
    --mysql-password "$MySqlPassword" `
    --followers $n `
    --rounds $Rounds `
    --poll-interval-ms $PollIntervalMs `
    --output ".\results\feed_benchmark_results.csv" `
    --detail-output ".\results\feed_benchmark_detail_$n.json"
}
