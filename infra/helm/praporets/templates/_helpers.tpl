{{/*
Спільні хелпери чарта. Використання в шаблонах — див. еталон
control-plane-deployment.yaml.
*/}}

{{/*
Повне ім'я образу сервісу. Викликається зі словником, бо define не має
доступу до зовнішнього контексту:
  {{ include "praporets.image" (dict "Values" .Values "name" "control-plane") }}
→ ghcr.io/koroljovpavel/praporets-control-plane:local
*/}}
{{- define "praporets.image" -}}
{{ .Values.image.registry }}/{{ .Values.image.owner }}/praporets-{{ .name }}:{{ .Values.image.tag }}
{{- end }}

{{/*
Спільні лейбли для metadata.labels будь-якого об'єкта.
*/}}
{{- define "praporets.labels" -}}
app.kubernetes.io/part-of: praporets
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end }}

{{/*
Селекторні лейбли: РІВНО те, чим Service знаходить поди, а Deployment —
свої репліки. Викликається зі словником: (dict "name" "control-plane").
Selector незмінний після створення Deployment — тому тут мінімум
(лише name), а не повний набір praporets.labels.
*/}}
{{- define "praporets.selectorLabels" -}}
app.kubernetes.io/name: {{ .name }}
{{- end }}
