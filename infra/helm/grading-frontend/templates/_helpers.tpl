{{- define "grading-frontend.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{- define "grading-frontend.fullname" -}}
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

{{- define "grading-frontend.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{- define "grading-frontend.labels" -}}
helm.sh/chart: {{ include "grading-frontend.chart" . }}
{{ include "grading-frontend.selectorLabels" . }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
app.kubernetes.io/part-of: grading
{{- end -}}

{{- define "grading-frontend.selectorLabels" -}}
app.kubernetes.io/name: {{ include "grading-frontend.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end -}}

{{- define "grading-frontend.serviceAccountName" -}}
{{- if .Values.serviceAccount.create -}}
{{- default (include "grading-frontend.fullname" .) .Values.serviceAccount.name -}}
{{- else -}}
{{- default "default" .Values.serviceAccount.name -}}
{{- end -}}
{{- end -}}

{{- define "grading-frontend.image" -}}
{{- $repo := .Values.image.repository -}}
{{- $tag := .Values.image.tag -}}
{{- $digest := .Values.image.digest -}}
{{- if $digest -}}
{{- printf "%s@%s" $repo $digest -}}
{{- else if $tag -}}
{{- if eq $tag "latest" -}}
{{- fail "image.tag=`latest` is not allowed." -}}
{{- end -}}
{{- printf "%s:%s" $repo $tag -}}
{{- else -}}
{{- fail "image.tag or image.digest is required." -}}
{{- end -}}
{{- end -}}
