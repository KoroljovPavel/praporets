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

{{/*
securityContext сервісних подів (04b, рішення I3) — однаковий для всіх
трьох сервісів, тому хелпер, а не values: безпека — не «настроюване»
середовищем. runAsUser НЕ задається: UID приходить з образу (UBI
openjdk-runtime — 185; native-образ edge отримує 185 через
quarkus.jib.user) — runAsNonRoot лише ПЕРЕВІРЯЄ його, і под із
образом-root чесно падає CreateContainerConfigError замість тихо
працювати від root.

Pod-рівень: {{ include "praporets.podSecurityContext" . | nindent 6 }}
*/}}
{{- define "praporets.podSecurityContext" -}}
securityContext:
  runAsNonRoot: true
  seccompProfile:
    type: RuntimeDefault
{{- end }}

{{/*
Контейнерний рівень: {{ include "praporets.containerSecurityContext" . | nindent 10 }}
readOnlyRootFilesystem вимагає emptyDir на /tmp (рішення I2):
Tomcat/Boot пише туди work-каталог, Vert.x/Quarkus — vertx-cache.
*/}}
{{- define "praporets.containerSecurityContext" -}}
securityContext:
  allowPrivilegeEscalation: false
  readOnlyRootFilesystem: true
  capabilities:
    drop: ["ALL"]
{{- end }}
