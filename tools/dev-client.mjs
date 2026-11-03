// Development clients without a Gradle build each: the Java command and environment of a client
// that Gradle started are saved once, and further clients are started from them under other
// player names, in other run folders and with other options. Saves a Gradle daemon's worth of
// memory per client on a small machine. The run's classes are read from the build folders, so a
// client started this way runs whatever was compiled last.
//
// node dev-client.mjs save <pid of a running client> <file>
// node dev-client.mjs start <file> <name> <run folder> <log file> [-Dkey=value ...] [-- program args ...]
import { openSync, readFileSync, writeFileSync } from 'node:fs';
import { spawn } from 'node:child_process';

const [command, ...rest] = process.argv.slice(2);

if (command === 'save') {
  const [pid, file] = rest;
  const args = readFileSync(`/proc/${pid}/cmdline`, 'utf8').split('\0').filter((part) => part !== '');
  const env = Object.fromEntries(
    readFileSync(`/proc/${pid}/environ`, 'utf8')
      .split('\0')
      .filter((part) => part.includes('='))
      .map((part) => [part.slice(0, part.indexOf('=')), part.slice(part.indexOf('=') + 1)]),
  );
  if (!args.includes('--username')) throw new Error(`no --username in the command of ${pid}`);
  writeFileSync(file, JSON.stringify({ args, env }));
  console.log(`saved the command of ${pid} to ${file}`);
} else if (command === 'start') {
  const [file, name, runDir, logFile, ...extra] = rest;
  const { args, env } = JSON.parse(readFileSync(file, 'utf8'));
  const split = extra.indexOf('--');
  const jvm = split < 0 ? extra : extra.slice(0, split);
  const program = split < 0 ? [] : extra.slice(split + 1);
  // The first client's own way into its world is not the next one's: its quick play and the
  // development LAN switch are left out, and the next client is given its own.
  const line = [];
  for (let i = 0; i < args.length; i++) {
    if (args[i].startsWith('--quickPlay')) {
      i++;
      continue;
    }
    if (args[i].startsWith('-Dmosaicary.devLan=')) continue;
    line.push(args[i]);
  }
  line[line.indexOf('--username') + 1] = name;
  // JVM options go right after the executable, before the class path and the main class.
  line.splice(1, 0, ...jvm);
  line.push(...program);
  const log = openSync(logFile, 'a');
  const child = spawn(line[0], line.slice(1), { cwd: runDir, env, detached: true, stdio: ['ignore', log, log] });
  child.unref();
  console.log(`started ${name} as ${child.pid} in ${runDir}`);
} else {
  throw new Error('usage: dev-client.mjs save <pid> <file> | start <file> <name> <run folder> <log> [-D...] [-- args]');
}
