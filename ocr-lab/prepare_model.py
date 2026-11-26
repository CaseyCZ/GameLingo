"""Explicit first-run preparation of the local EN-CS model."""
import argostranslate.package as package
from engines import local_translator

if __name__=="__main__":
    if not any(p.from_code=="en" and p.to_code=="cs" for p in package.get_installed_packages()):
        package.update_package_index()
        models=[p for p in package.get_available_packages() if p.from_code=="en" and p.to_code=="cs"]
        if not models:raise RuntimeError("EN-CS model is unavailable.")
        print("Installing EN-CS",models[0].package_version,flush=True)
        package.install_from_path(models[0].download())
    print("Zkušební překlad:",local_translator()("Please wait here."),flush=True)
