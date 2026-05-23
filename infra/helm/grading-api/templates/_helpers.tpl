{{/*
  Standard naming + labels helpers.
*/}}

{{- define "grading-api.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{- define "grading-api.fullname" -}}
{{- if .Values.fullnameOverride -}}
{{- .Values.fullnameOverride | trunc 63 | trimSuffix "-" -}}
{{- else -}}
{{- $name := default .Chart.Name .Values.nameOverride -}}
{{- if contains $name .Release.Name -}}
{{- .Release.Name | trunc 63 | trimSuffix "-" -}}
{{- else -}}
{{- printf "%s-%s" .Release.Name $name | trunc 63 | trimSuffix "-" -}}
{{- end -}}
{{- end -}}
{{- end -}}

{{- define "grading-api.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{- define "grading-api.labels" -}}
helm.sh/chart: {{ include "grading-api.chart" . }}
{{ include "grading-api.selectorLabels" . }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
app.kubernetes.io/part-of: grading
{{- end -}}

{{- define "grading-api.selectorLabels" -}}
app.kubernetes.io/name: {{ include "grading-api.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end -}}

{{- define "grading-api.serviceAccountName" -}}
{{- if .Values.serviceAccount.create -}}
{{- default (include "grading-api.fullname" .) .Values.serviceAccount.name -}}
{{- else -}}
{{- default "default" .Values.serviceAccount.name -}}
{{- end -}}
{{- end -}}

{{/*
  Render the container image reference.
  Hard error if image.tag is empty (we forbid `latest` everywhere).
  Prefer digest pin when supplied.
*/}}
{{- define "grading-api.image" -}}
{{- $repo := .Values.image.repository -}}
{{- $tag := .Values.image.tag -}}
{{- $digest := .Values.image.digest -}}
{{- if $digest -}}
{{- printf "%s@%s" $repo $digest -}}
{{- else if $tag -}}
{{- if eq $tag "latest" -}}
{{- fail "image.tag=`latest` is not allowed. Pin a SemVer or sha-<shortsha> tag." -}}
{{- end -}}
{{- printf "%s:%s" $repo $tag -}}
{{- else -}}
{{- fail "image.tag or image.digest is required. CI must --set image.tag=<sha-or-version>." -}}
{{- end -}}
{{- end -}}

{{- define "grading-api.migrator.image" -}}
{{- $repo := .Values.migrator.image.repository | default .Values.image.repository -}}
{{- $tag := .Values.migrator.image.tag | default .Values.image.tag -}}
{{- if eq $tag "latest" -}}
{{- fail "migrator.image.tag=`latest` is not allowed." -}}
{{- end -}}
{{- if not $tag -}}
{{- fail "migrator.image.tag is required." -}}
{{- end -}}
{{- printf "%s:%s" $repo $tag -}}
{{- end -}}
