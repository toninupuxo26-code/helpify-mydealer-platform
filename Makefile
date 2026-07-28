.PHONY: help hash-evidence verify-evidence release-manifest status doctor doctor-host doctor-repo patch-list patch-status patch-check patch-verify backend-doctor auth-doctor web-auth-doctor helpify-workflow-doctor admin-doctor

help:
	@echo "make hash-evidence EVIDENCE_DIR=/path/to/originals"
	@echo "make verify-evidence MANIFEST=path/to/SHA256SUMS"
	@echo "make release-manifest ARTIFACT_DIR=/path/to/artifacts"
	@echo "make doctor | doctor-host | doctor-repo"
	@echo "make patch-list | patch-status | patch-check | patch-verify"
	@echo "make backend-doctor | auth-doctor | web-auth-doctor | helpify-workflow-doctor | admin-doctor"
	@echo "make status"

hash-evidence:
	@test -n "$(EVIDENCE_DIR)" || (echo "EVIDENCE_DIR is required" && exit 1)
	./scripts/hash_evidence.sh "$(EVIDENCE_DIR)"

verify-evidence:
	@test -n "$(MANIFEST)" || (echo "MANIFEST is required" && exit 1)
	./scripts/verify_evidence.sh "$(MANIFEST)"

release-manifest:
	@test -n "$(ARTIFACT_DIR)" || (echo "ARTIFACT_DIR is required" && exit 1)
	./scripts/create_release_manifest.sh "$(ARTIFACT_DIR)"

doctor:
	./scripts/doctor.sh all

doctor-host:
	./scripts/doctor.sh host

doctor-repo:
	./scripts/doctor.sh repo

patch-list:
	./scripts/patchctl.sh list

patch-status:
	./scripts/patchctl.sh status

patch-check:
	./scripts/patchctl.sh check all

patch-verify:
	./scripts/patchctl.sh verify all

backend-doctor:
	./scripts/backend_doctor.sh all

auth-doctor:
	./scripts/auth_doctor.sh all

web-auth-doctor:
	./scripts/web_auth_doctor.sh all

helpify-workflow-doctor:
	./scripts/helpify_workflow_doctor.sh all

admin-doctor:
	./scripts/admin_doctor.sh all

status:
	@git status --short --branch
	@echo "Version: $$(cat VERSION)"
