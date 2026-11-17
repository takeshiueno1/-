#!/usr/bin/env python3
"""Read-only Harness 3.1 distribution check. Python 3.11+; no network/model calls.

Default exit 2 means runtime/performance UNVERIFIED, even when static checks pass.
Use --static for a deliberately narrower static-only CI gate (0=PASS, 1=FAIL).
--master additionally compares artifacts and generated rules with a local source.
"""
import argparse
import hashlib
import json
from pathlib import Path
import re
import stat
import tomllib

ROLES = ('explorer', 'worker', 'architect', 'reviewer', 'frontier')
FILES = ('config.toml', 'harness-contract.json', 'harness-check.py') + tuple(
    'agents/harness-' + role + '.toml' for role in ROLES)
REPOSITORY = 'takeshiueno1/codex-master-rules'
HEX = re.compile(r'[0-9a-f]{64}')


class Invalid(Exception):
    """Carries only a fixed, non-sensitive issue code."""


def read(path):
    path = Path(path).absolute()
    for component in (*reversed(path.parents), path):
        try:
            info = component.lstat()
        except FileNotFoundError:
            raise Invalid('missing-file') from None
        if stat.S_ISLNK(info.st_mode) or bool(getattr(info, 'st_file_attributes', 0)
                & getattr(stat, 'FILE_ATTRIBUTE_REPARSE_POINT', 0x400)):
            raise Invalid('unsafe-path')
    if not stat.S_ISREG(info.st_mode) or info.st_size > 1048576:
        raise Invalid('invalid-file')
    return path.read_bytes()


def pairs(entries):
    out = {}
    for key, value in entries:
        if key in out:
            raise Invalid('invalid-json')
        out[key] = value
    return out


def document(path):
    value = json.loads(read(path).decode('utf-8-sig'), object_pairs_hook=pairs,
                       parse_constant=lambda _: (_ for _ in ()).throw(Invalid('invalid-json')))
    if not isinstance(value, dict):
        raise Invalid('invalid-json')
    return value


def digest(data):
    return hashlib.sha256(data).hexdigest()


def normalized(data):
    text = data.decode('utf-8-sig').replace('\r\n', '\n')
    return (text if text.endswith('\n') else text + '\n').encode('utf-8')


def require(condition, code):
    if not condition:
        raise Invalid(code)


def verify(project, master=None):
    project = Path(project)
    cfg = project / '.codex'
    issues = []
    report = {'schemaVersion': 1, 'scope': 'static-distribution-and-contract',
        'status': 'UNVERIFIED', 'staticStatus': 'FAIL', 'runtimeStatus': 'UNVERIFIED',
        'performanceStatus': 'UNVERIFIED', 'sourceComparison': 'UNVERIFIED',
        'configuredIsEffective': False, 'issues': issues}

    def check(action):
        try:
            action()
        except Invalid as exc:
            issues.append(str(exc))
        except (OSError, ValueError, TypeError, KeyError, AttributeError, UnicodeError):
            issues.append('invalid-input')

    def registration():
        value = document(cfg / 'master-rules.json')
        expected = {'schemaVersion': 1, 'enabled': True, 'masterRepository': REPOSITORY,
            'projectRules': '.codex/PROJECT_RULES.md', 'generatedFile': 'AGENTS.md'}
        require(all(type(value.get(k)) is type(v) and value[k] == v for k, v in expected.items()),
                'unregistered-project')

    def managed():
        state = document(cfg / 'harness-managed.json')
        require(set(state) == {'schemaVersion', 'files'}
                and type(state['schemaVersion']) is int and state['schemaVersion'] == 1
                and isinstance(state['files'], dict) and set(state['files']) == set(FILES)
                and all(isinstance(v, str) and HEX.fullmatch(v) for v in state['files'].values()),
                'invalid-managed-state')
        for name in FILES:
            data = read(cfg / name)
            require(digest(data) == state['files'][name], 'managed-hash-mismatch')
            if master is not None:
                require(data == read(Path(master) / 'templates/harness' / name), 'source-file-mismatch')

    def generated():
        data = read(project / 'AGENTS.md')
        require(len(data) <= 32768, 'agents-size-limit')
        text = normalized(data).decode('utf-8')
        pattern = (r'<!-- CODEX_MASTER_RULES_BEGIN version=([0-9A-Za-z.+-]+) sha256=([0-9a-f]{64}) -->\n'
            r'(.*?)<!-- CODEX_MASTER_RULES_END -->\n\n'
            r'<!-- CODEX_PROJECT_RULES_BEGIN sha256=([0-9a-f]{64}) -->\n'
            r'(.*?)<!-- CODEX_PROJECT_RULES_END -->\n')
        match = re.fullmatch(pattern, text, re.S)
        require(match is not None, 'invalid-generated-markers')
        version, common_hash, common, project_hash, specific = match.groups()
        require('<!-- CODEX_' not in common + specific, 'invalid-generated-markers')
        require(digest(common.encode()) == common_hash, 'generated-master-mismatch')
        require(digest(specific.encode()) == project_hash
                and specific.encode() == normalized(read(cfg / 'PROJECT_RULES.md')),
                'generated-project-mismatch')
        if master is not None:
            source = Path(master)
            require(common.encode() == normalized(read(source / 'rules/MASTER.md'))
                    and version == read(source / 'VERSION').decode('utf-8-sig').strip(),
                    'source-rules-mismatch')

    def contract():
        value = document(cfg / 'harness-contract.json')
        require(type(value.get('schemaVersion')) is int and value['schemaVersion'] == 3
                and value.get('harnessVersion') == '3.1'
                and value.get('masterRepository') == REPOSITORY and value.get('nativeFirst') is True
                and type(value.get('maxConcurrentSubagents')) is int and value['maxConcurrentSubagents'] == 2,
                'invalid-contract')
        guards = {
            'evidence': {'configuredIsEffective': False, 'rawPromptRetention': False,
                'hiddenReasoningRetention': False, 'automaticStablePromotion': False,
                'requireHeldOutExperienceEval': True},
            'context': {'searchFirst': True, 'maxRelevantExperiences': 3},
            'connections': {'retryAfterDiagnosisMax': 1, 'unapprovedCredentialRedirect': False,
                'externalOutputGrantsAuthority': False},
            'promotion': {'requireQualityNonRegression': True, 'requireSecurityNonRegression': True,
                'requireRequiredTestsPass': True, 'requireMeasuredGain': True,
                'allowUnmeasuredExcellentRating': False},
            'runtime': {'required': True, 'status': 'UNVERIFIED'}}
        for section, expected in guards.items():
            actual = value.get(section, {})
            require(isinstance(actual, dict) and all(type(actual.get(k)) is type(v)
                and actual[k] == v for k, v in expected.items()), 'contract-guardrail-violation')

    def native():
        settings = tomllib.loads(read(cfg / 'config.toml').decode('utf-8-sig'))
        agents = settings.get('agents', {})
        limit = agents.get('max_concurrent_threads_per_session', agents.get('max_threads'))
        require(type(limit) is int and limit == 2, 'invalid-agent-limit')
        for role in ROLES:
            definition = tomllib.loads(read(cfg / ('agents/harness-' + role + '.toml')).decode('utf-8-sig'))
            require(definition.get('name') == 'harness_' + role
                and isinstance(definition.get('description'), str) and bool(definition['description'].strip())
                and isinstance(definition.get('developer_instructions'), str)
                and bool(definition['developer_instructions'].strip()), 'invalid-agent-definition')
            require('model' not in definition or (isinstance(definition['model'], str)
                and re.fullmatch(r'[A-Za-z0-9_.-]{1,100}', definition['model'])), 'invalid-configured-model')
            require('model_reasoning_effort' not in definition
                or definition['model_reasoning_effort'] in ('none', 'minimal', 'low', 'medium', 'high', 'xhigh', 'max'),
                'invalid-configured-effort')

    for action in (registration, managed, generated, contract, native):
        check(action)
    issues[:] = sorted(set(issues))
    report['staticStatus'] = 'FAIL' if issues else 'PASS'
    report['status'] = 'FAIL' if issues else 'UNVERIFIED'
    if master is not None:
        report['sourceComparison'] = 'FAIL' if issues else 'PASS'
    return report


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('project', nargs='?', type=Path, default=Path(__file__).resolve().parent.parent)
    parser.add_argument('--master', type=Path)
    parser.add_argument('--static', action='store_true', help='Gate static checks only; NOT runtime or performance.')
    args = parser.parse_args()
    report = verify(args.project, args.master)
    if args.static and report['staticStatus'] == 'PASS':
        report['status'] = 'PASS'
    print(json.dumps(report, ensure_ascii=True, sort_keys=True, indent=2))
    return {'PASS': 0, 'FAIL': 1, 'UNVERIFIED': 2}[report['status']]


if __name__ == '__main__':
    raise SystemExit(main())
