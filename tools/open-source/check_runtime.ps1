param(
    [string]$BaseUrl = 'http://127.0.0.1:4320',
    [ValidateSet('Mock', 'LiveReadiness')][string]$Mode = 'LiveReadiness'
)
$ErrorActionPreference = 'Stop'
if ([string]::IsNullOrWhiteSpace($env:OCH_ACCESS_TOKEN)) {
    throw 'Set OCH_ACCESS_TOKEN to a logged-in administrator token'
}
$headers = @{ Authorization = 'Bearer ' + $env:OCH_ACCESS_TOKEN }
$base = $BaseUrl.TrimEnd('/')
$status = Invoke-RestMethod -Method Post -Uri ($base + '/asr/v1/admin/status') -Headers $headers
if ($status.code -ne 200) { throw 'ASR status query was rejected' }
if ($Mode -eq 'LiveReadiness') {
    $data = $status.data
    if (-not $data.enabled -or [string]::IsNullOrWhiteSpace($data.resolvedProvider) -or
        $data.resolvedProvider -eq 'MOCK' -or -not $data.readyProviders.($data.resolvedProvider) -or
        -not $data.freeSwitch.eslCommandReady -or -not $data.freeSwitch.audioStreamOperational -or
        -not $data.freeSwitch.streamTokenConfigured) {
        throw 'Real ASR is not ready: check provider, ESL and the audio streaming module'
    }
    Write-Output 'Provider and audio-stream prerequisites are ready. A real bridged call is still required.'
    exit 0
}
if (-not $status.data.enabled -or $status.data.resolvedProvider -ne 'MOCK') {
    throw 'Mock mode requires resolvedProvider=MOCK; no synthetic audio was sent'
}
$callId = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
$body = @{
    callId = $callId
    uniqueId = 'public-smoke-' + $callId
    sampleRate = 16000
    channels = 1
    frameDurationMs = 20
    base64Pcm = [Convert]::ToBase64String((New-Object byte[] 32000))
} | ConvertTo-Json -Compress
$result = Invoke-RestMethod -Method Post -Uri ($base + '/asr/v1/admin/simulate') `
    -Headers $headers -ContentType 'application/json' -Body $body
if ($result.code -ne 200 -or $result.data.resolvedProvider -ne 'MOCK' -or
    $result.data.framesSent -le 0 -or $null -eq $result.data.summary -or
    $result.data.summary.provider -ne 'MOCK' -or
    $result.data.summary.status -ne 'SUCCESS' -or
    [string]::IsNullOrWhiteSpace($result.data.summary.fullText) -or
    $result.data.summary.segmentCount -le 0 -or
    -not [string]::IsNullOrWhiteSpace($result.data.summary.errorCode)) {
    throw 'Mock smoke check failed: provider, text, segments or persisted summary are invalid'
}
$persisted = Invoke-RestMethod -Method Post -Uri ($base + '/asr/v1/transcript/getByCallId/' + $callId) -Headers $headers
if ($persisted.code -ne 200 -or $null -eq $persisted.data -or
    [string]$persisted.data.callId -ne [string]$callId -or
    $persisted.data.provider -ne 'MOCK' -or
    $persisted.data.fullText -ne $result.data.summary.fullText -or
    $persisted.data.segmentCount -ne $result.data.summary.segmentCount) {
    throw 'Mock summary could not be read back by callId'
}
Write-Output ('Mock ingestion and persistence passed; callId=' + $callId + '. This does not measure speech recognition.')
