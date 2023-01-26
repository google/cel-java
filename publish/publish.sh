#!/bin/bash
#
# Publishes CEL to a Maven repository.
#
# To push locally, just run the script
# To publish a SNAPSHOT version, run with -snapshot (-s) flag: "publish.sh --snapshot"
# To publish a RELEASE version remotely, run with -release (-r) flag: "publish.sh --release". Note that released maven artifacts are permanent and can never be deleted under any circumstances.

# Note: To publish, you must create a pgp certificate and upload it to keyserver.ubuntu.com. See https://blog.sonatype.com/2010/01/how-to-generate-pgp-signatures-with-maven/

RUNTIME_TARGET=//publish:cel_runtime.publish

function publish_maven_remote() {
  maven_repo_url=$1
   # Credentials should be read from maven config (settings.xml) once it
   # is supported by bazelbuild:
   # https://github.com/bazelbuild/rules_jvm_external/issues/679
   read -p "maven_user: " maven_user
   read -s -p "maven_password: " maven_password
   bazel run --stamp \
     --define "maven_repo=$maven_repo_url" \
     --define gpg_sign=true \
     --define "maven_user=$maven_user" \
     --define "maven_password=$maven_password" \
     $RUNTIME_TARGET
}

version=$(<cel_version.bzl)
version=$(echo $version | cut -f2 -d = | xargs)

flag=$1
if [ "$flag" == "--snapshot" ] || [ "$flag" == "-s" ]; then
  if ! [[ $version == *"-SNAPSHOT"* ]]; then
    echo "Unable to publish. Please append -SNAPSHOT suffix to CEL Version"
    exit 1;
  fi
  echo "Publishing a SNAPSHOT version: $version to remote Maven repository"
  publish_maven_remote "https://s01.oss.sonatype.org/content/repositories/snapshots/"
elif [ "$flag" == "--release" ] || [ "$flag" == "-r" ]; then
  if [[ $version == *"-SNAPSHOT"* ]]; then
   echo "Unable to publish. Please remove -SNAPSHOT suffix from CEL Version"
   exit 1;
  fi
  printf "This will publish the following CEL Version to the Central Maven Repository:\n\n%s\n\nTHIS CANNOT BE UNDONE! PLEASE DOUBLE CHECK THE VERSION AT THIS TIME:\n(I.E: Check that the version is appropriately incremented based on Semantic Versioning).\n\n" "$version"
 while true; do
     read -p "Proceed (Y/N)? " yn
     case $yn in
         [Yy]* )
           publish_maven_remote "https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/"
           break;;
         [Nn]* ) exit;;
         * ) echo "Please answer yes or no.";;
     esac
 done
else
 local_maven_repo=$HOME/.m2/repository
 echo "Pushing to local Maven repository $local_maven_repo"
 bazel run --define "maven_repo=file://$local_maven_repo" $RUNTIME_TARGET
fi
