#!/usr/bin/env python3
"""Build and sign the experimental HarmonyOS app with local debug credentials."""
import argparse
import os
from pathlib import Path
import subprocess
import sys


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--harmony-home', type=Path, default=Path.home() / 'Maindome/harmony')
    parser.add_argument('--install', action='store_true', help='Install and launch on an authorized device')
    parser.add_argument('--target', help='hdc device ID')
    args = parser.parse_args()
    home = args.harmony_home.resolve()
    tools = home / 'command-line-tools'
    sdk = tools / 'sdk/default/openharmony'
    app = Path(__file__).resolve().parents[1] / 'experimental/harmony-app'
    signing = home / 'signing'
    env = os.environ.copy()
    env.update({
        'DEVECO_SDK_HOME': str(tools / 'sdk'),
        'DEVECO_NODE_HOME': str(tools / 'tool/node'),
        'JAVA_HOME': str(home / 'jdk21'),
        'HVIGOR_USER_HOME': str(home / 'cache/hvigor'),
        'TMPDIR': str(home / 'tmp'),
        'npm_config_cache': str(home / 'cache/npm'),
    })
    env['PATH'] = ':'.join([str(home / 'jdk21/bin'), str(tools / 'tool/node/bin'),
                            str(tools / 'bin'), env['PATH']])
    for folder in ('tmp', 'cache/npm', 'cache/hvigor', 'cache/ohpm/cache', 'build'):
        (home / folder).mkdir(parents=True, exist_ok=True)
    required = [tools / 'bin/hvigorw', tools / 'bin/ohpm', home / 'jdk21/bin/java',
                signing / 'maa-harmony-debug.p12', signing / 'keystore-password.txt',
                signing / 'maa-debug-leaf-first-chain.cer', signing / 'MAADebug.p7b']
    for path in required:
        if not path.is_file():
            raise RuntimeError(f'Missing required local file: {path}')
    password = (signing / 'keystore-password.txt').read_text().strip()
    if not password:
        raise RuntimeError('Empty keystore password file')

    def run(label, command, timeout=300):
        print(label, flush=True)
        result = subprocess.run([str(x) for x in command], cwd=app, env=env,
                                capture_output=True, text=True, timeout=timeout)
        output = result.stdout + result.stderr
        if result.returncode or '[Fail]' in output:
            # Do not expose credentials through exception command representations.
            print(output.replace(password, '[REDACTED]'), file=sys.stderr)
            raise RuntimeError(f'{label} failed (exit {result.returncode})')
        return output

    run('Install module dependencies', [tools / 'bin/ohpm', 'install', '--all',
                                       '--cache', home / 'cache/ohpm/cache'])
    build = run('Build debug HAP', [tools / 'bin/hvigorw', 'assembleHap', '--mode', 'module',
                                   '-p', 'product=default', '-p', 'buildMode=debug', '--no-daemon'])
    if 'BUILD SUCCESSFUL' not in build:
        raise RuntimeError('Build did not report success')
    unsigned = app / 'entry/build/default/outputs/default/entry-default-unsigned.hap'
    signed = home / 'build/maa-harmony-probe-signed.hap'
    jar = sdk / 'toolchains/lib/hap-sign-tool.jar'
    run('Sign HAP', ['java', '-jar', jar, 'sign-app', '-mode', 'localSign',
        '-keyAlias', 'maa-harmony-debug', '-appCertFile', signing / 'maa-debug-leaf-first-chain.cer',
        '-profileFile', signing / 'MAADebug.p7b', '-inFile', unsigned,
        '-signAlg', 'SHA256withECDSA', '-keystoreFile', signing / 'maa-harmony-debug.p12',
        '-keystorePwd', password, '-keyPwd', password, '-outFile', signed,
        '-compatibleVersion', '26', '-signCode', '1'])
    run('Verify signed HAP', ['java', '-jar', jar, 'verify-app', '-inFile', signed,
        '-outCertChain', home / 'build/verified-chain.cer',
        '-outProfile', home / 'build/verified-profile.p7b'])
    print(f'Signed artifact: {signed}')
    if args.install:
        hdc = [sdk / 'toolchains/hdc']
        if args.target:
            hdc += ['-t', args.target]
        installed = run('Install HAP', hdc + ['install', signed])
        if 'install bundle successfully' not in installed:
            raise RuntimeError('Device did not confirm installation')
        launched = run('Launch app', hdc + ['shell', 'aa', 'start', '-b',
                                           'com.maameow.hmosprobe', '-a', 'EntryAbility'])
        if 'start ability successfully' not in launched:
            raise RuntimeError('App did not launch; unlock the phone and retry')
        print('Installed and launched com.maameow.hmosprobe')


if __name__ == '__main__':
    try:
        main()
    except subprocess.TimeoutExpired:
        print('Error: operation timed out', file=sys.stderr)
        sys.exit(1)
    except (OSError, RuntimeError) as error:
        print(f'Error: {error}', file=sys.stderr)
        sys.exit(1)
