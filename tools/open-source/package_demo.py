"""Stage a MySQL demo from verified sanitized build artifacts, never site target/dist."""
import argparse
import hashlib
import importlib.util
import io
import json
import shutil
import zipfile
from pathlib import Path


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def package(root, frontend, build, output, nginx):
    if output.exists():
        raise ValueError("Use a NEW output directory")
    spec = importlib.util.spec_from_file_location("export_release", root / "tools/open-source/export_release.py")
    release = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(release)
    release.export(root, output / "source-release", frontend, database="mysql")
    exported = output / "source-release/source"
    # Ensure the supplied compiled artifacts actually match these application sources.
    compared = 0
    for path in exported.rglob("*"):
        if not path.is_file():
            continue
        relative = path.relative_to(exported)
        relevant = relative.parts[0].startswith("och-") or relative.as_posix() == "pom.xml" or relative.parts[0] == "frontend"
        if relevant:
            built_from = build / relative
            if not built_from.is_file() or digest(path) != digest(built_from):
                raise ValueError(f"Artifact source differs: {relative}")
            compared += 1
    stage = output / "XinhengZhishu-MySQL-Demo"
    shutil.copytree(root / "deploy/demo", stage,
                    ignore=lambda directory, names: [name for name in names if name.lower().endswith('.md')])
    shutil.copyfile(exported / "README.md", stage / "README.md")
    shutil.copytree(exported / "assets", stage / "assets")
    upstream_hashes={
        'freeswitch.tar.gz':'6ab49ae26f45e35d3b88ae12b5d915a10aeabafcc0fd24a427c3bab4012c9e55',
        'sofia-sip.tar.gz':'a63ba972e4b7839b50bfd1277e150ebdb70ecd9261894bc65a74a7d3e995d533',
        'spandsp.tar.gz':'1cb9a0ee3a68a20e84db9702cb82ac643b27e15820e74eb5a69511952dfa27b2',
    }
    for name,expected in upstream_hashes.items():
        path=stage/'docker/freeswitch/upstream'/name
        if path.exists() and digest(path)!=expected: raise ValueError('Unexpected upstream source checksum: '+name)
    forbidden = [p for p in stage.rglob('*') if p.is_file() and (p.name in {'.env','demo.env'} or p.suffix in {'.log','.rdb','.pid'})]
    if forbidden:
        raise ValueError('Private or runtime file entered deployment templates')
    (stage / "app").mkdir()
    jar = build / "och-api/target/och-api-0.0.1.jar"
    shutil.copyfile(jar, stage / "app/och-api.jar")
    shutil.copytree(build / "frontend/dist", stage / "web")
    for directory in ["source", "licenses/nginx", "bin/nginx"]:
        (stage / directory).mkdir(parents=True, exist_ok=True)
    shutil.copyfile(output / "source-release/OpenCallHub-public-source.zip", stage / "source/OpenCallHub-public-source.zip")
    shutil.copyfile(output / "source-release/SHA256SUMS", stage / "source/SHA256SUMS")
    shutil.copyfile(root / "LICENSE", stage / "LICENSE")
    shutil.copyfile(frontend / "LICENSE", stage / "licenses/frontend-LICENSE")
    shutil.copyfile(nginx / "nginx.exe", stage / "bin/nginx/nginx.exe")
    shutil.copyfile(nginx / "conf/mime.types", stage / "config/mime.types")
    for name in ["LICENSE","OpenSSL.LICENSE","PCRE.LICENCE","zlib.LICENSE"]:
        shutil.copyfile(nginx / "docs" / name, stage / "licenses/nginx" / name)
    with zipfile.ZipFile(jar) as archive:
        libraries = sorted(n for n in archive.namelist() if n.startswith("BOOT-INF/lib/") and n.endswith(".jar"))
        for prefix, target in [("mysql-connector-j-","mysql-connector-j.jar"),
                               ("spring-security-crypto-","spring-security-crypto.jar"),
                               ("spring-jcl-","spring-jcl.jar")]:
            names = [n for n in libraries if Path(n).name.startswith(prefix)]
            if len(names) != 1:
                raise ValueError(f"Expected one library: {prefix}")
            (stage / "tools" / target).write_bytes(archive.read(names[0]))
        for n in libraries:
            if any(p in n for p in ["mysql-connector-j-","och-common-"]):
                with zipfile.ZipFile(io.BytesIO(archive.read(n))) as lib:
                    for license_name in lib.namelist():
                        if Path(license_name).name.upper().startswith(("LICENSE","NOTICE","COPYING")) and not license_name.endswith('/'):
                            target = stage / "licenses" / Path(n).stem / Path(license_name).name
                            target.parent.mkdir(parents=True, exist_ok=True)
                            target.write_bytes(lib.read(license_name))
    dependency_sources = root / ".codex-build/mysql-demo/dependency-sources"
    for name in ["och-common-1.0.1-sources.jar","mysql-connector-j-9.5.0-sources.jar"]:
        shutil.copyfile(dependency_sources / name, stage / "source" / name)
    third_party = ("\n\n## Deployment dependencies\n\nBackend and och-common: GPLv3 / GPL-3.0-only; retain LICENSE and the source notices in this README.\n"
                   "Frontend: Apache-2.0; see licenses/frontend-LICENSE.\n"
                   "Windows nginx: see licenses/nginx, including OpenSSL, PCRE and zlib notices.\n"
                   "MySQL Connector/J 9.5.0: see embedded LICENSE; matching source supplied.\n"
                   "MySQL server, Redis, JDK images and provider services are not redistributed.\n"
                   "FreeSWITCH, Sofia-SIP and SpanDSP upstream sources may be supplied under docker/freeswitch/upstream; retain their embedded license texts.\n"
                   "Other Java dependency notices remain embedded inside each JAR.\n\n"
                   "Corresponding project source: source/OpenCallHub-public-source.zip\n"
                   "Common source: https://repo.maven.apache.org/maven2/io/github/sxwdmjy/och-common/1.0.1/\n"
                   "Connector source: https://repo.maven.apache.org/maven2/com/mysql/mysql-connector-j/9.5.0/\n\n## Embedded libraries\n\n")
    with (stage / "README.md").open("a", encoding="utf-8") as readme:
        readme.write(third_party + ''.join('- ' + Path(n).name + '\n' for n in libraries))
    info = {"brand":"信恒知枢·呼叫中心","database":"MySQL 8.4","source_files_compared":compared,
            "source_sha256":digest(stage / "source/OpenCallHub-public-source.zip"),"backend_sha256":digest(stage / "app/och-api.jar"),
            "build":"JDK 17 / Maven / Vite production; sanitized source only", "runtime":"See README.md for the current validation scope"}
    (stage / "build-info.json").write_text(json.dumps(info,ensure_ascii=False,indent=2)+'\n',encoding="utf-8")
    return stage


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ["root","frontend","build","output","nginx"]:
        parser.add_argument('--'+name,type=Path,required=True)
    args = parser.parse_args()
    print(package(**{k:v.resolve() for k,v in vars(args).items()}))
