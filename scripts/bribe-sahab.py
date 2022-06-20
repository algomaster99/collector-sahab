import argparse
import json
import os
import shlex
import subprocess
from glob import glob

from revision import REVISION
from compile_target import compile

CONFIG = json.load(open(os.path.join(os.path.dirname(__file__), 'config.json')))

COLLECTOR_JAR = CONFIG.get('collectorJar', None)
OUTPUT_DIRECTORY = CONFIG.get('outputDirectory', '')

class VerifyDirectory(argparse.Action):
  def __call__(self, parser, namespace, directory, option_string=None):
    if not os.path.isdir(directory):
        raise argparse.ArgumentTypeError(f"{directory} is not a valid directory")
    if not "pom.xml" in os.listdir(directory):
      raise argparse.ArgumentTypeError(f"{directory} is not a maven project")
    setattr(namespace, self.dest, directory)

parser = argparse.ArgumentParser("Bribe sahab")
parser.add_argument("-p", "--project", action=VerifyDirectory, required=True, help="Path to project")
parser.add_argument("-l", "--left", required=True, help="Left revision")
parser.add_argument("-r", "--right", required=True, help="Right revision")

# Specifically for finding matched lines
parser.add_argument("-c", "--class-filename", required=True, help="Name of the file which contains the patch")

# For collector sahab
parser.add_argument("-t", "--tests", required=True, nargs="+", help="Tests executed by collector sahab")


def _compile_target(project, left, right):
  compile(project, left, REVISION.LEFT)
  compile(project, right, REVISION.RIGHT)


def _find_matched_lines(project, filename, left, right):
  cmd = (
    "java "
    f"-classpath {COLLECTOR_JAR} "
    "se.kth.debug.MatchedLineFinder "
    f"{project} {shlex.quote(filename)} {left} {right}"
  )
  subprocess.run(cmd, shell=True)


def _get_or_create_directory_for_creating_output_files(ref):
  output_directory = os.path.join(OUTPUT_DIRECTORY, "sahab-reports", ref)
  if not os.path.exists(output_directory):
    os.makedirs(os.path.join(OUTPUT_DIRECTORY, "sahab-reports", ref))
  return output_directory

def _run_collector_sahab(project, tests, revision, ref):
  all_targets = glob(f"{project}/**/{revision.value.get_output_directory()}/", recursive=True)
  project_dependencies = []
  for build_dir in all_targets:
    project_dependencies.append(os.path.join(build_dir, 'classes'))
    project_dependencies.append(os.path.join(build_dir, 'test-classes'))
    project_dependencies.append(os.path.join(build_dir, 'dependency'))

  test_methods = " ".join(tests)
  output_directory = _get_or_create_directory_for_creating_output_files(ref)
  collector_sahab_output = os.path.join(output_directory, f"{revision.name.lower()}.json")

  cmd = (
    "java "
    f"-jar {COLLECTOR_JAR} "
    f"-i {revision.value.get_input_file()} "
    f"-p {' '.join(project_dependencies)} "
    f"-t {test_methods} "
    f"-o {collector_sahab_output} "
    f"-m methods.json"
  )
  print(cmd)

  subprocess.run(cmd, shell=True)


def main():
  args = parser.parse_args()
  project = args.project
  left_revision_ref = args.left
  right_revision_ref = args.right

  classname = args.class_filename

  tests = args.tests

  _compile_target(project, left_revision_ref, right_revision_ref)
  _find_matched_lines(project, classname, left_revision_ref, right_revision_ref)
  _run_collector_sahab(project, tests, REVISION.LEFT, right_revision_ref)
  _run_collector_sahab(project, tests, REVISION.RIGHT, right_revision_ref)

if __name__ == "__main__":
  main()
