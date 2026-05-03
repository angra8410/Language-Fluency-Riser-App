# AGENTE.md

Este proyecto es una app Android para práctica de fluidez de idiomas conectada a Ollama.

## Reglas de trabajo

1. No repitas exploraciones del repositorio si ya identificaste los archivos relevantes.
2. No entres en ciclos de planificación. Haz como máximo una inspección inicial breve y luego modifica archivos.
3. Trabaja en cambios pequeños e incrementales.
4. No intentes resolver UI/UX, Settings, STT y arquitectura en una sola pasada.
5. Después de cada cambio, resume archivos modificados y cómo probarlos.
6. Si falta información, usa supuestos razonables y avanza.
7. No cambies la lógica base de conexión con Ollama salvo que sea necesario para hacer configurable la Base URL.
8. Mantén compatibilidad con endpoints existentes `/api/chat` o `/api/generate`.
9. No elimines funcionalidad existente.
10. Prioriza que el proyecto compile.

## Flujo esperado

Para cada tarea:

1. Inspecciona solo los archivos necesarios.
2. Identifica los archivos a modificar.
3. Aplica el cambio.
4. Ejecuta o indica el comando de build.
5. Resume resultado.

## Límite anti-loop

Si ya hiciste una inspección inicial, no vuelvas a decir “voy a inspeccionar”. Continúa con la modificación concreta.

Si una tarea es muy grande, implementa solo la primera parte funcional y deja el resto como siguiente paso.