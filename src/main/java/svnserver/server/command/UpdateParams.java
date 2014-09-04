package svnserver.server.command;

import org.jetbrains.annotations.NotNull;

/**
 * Update between revisions.
 * <pre>
 * update
 *    params:   ( [ rev:number ] target:string recurse:bool
 *    ? depth:word send_copyfrom_args:bool ? ignore_ancestry:bool )
 *    Client switches to report command set.
 *    Upon finish-report, server sends auth-request.
 *    After auth exchange completes, server switches to editor command set.
 *    After edit completes, server sends response.
 *    response: ( )
 * </pre>
 *
 * @author Marat Radchenko <marat@slonopotamus.org>
 */
public final class UpdateParams extends DeltaParams {

  public UpdateParams(@NotNull int[] rev, @NotNull String target, boolean recurse, @NotNull String depth, boolean sendCopyFromArgs, boolean ignoreAncestry) {
    super(rev, target, null, true, recurse, depth, sendCopyFromArgs, ignoreAncestry);
  }
}